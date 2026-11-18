package tf.dpi.ingestion;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.Test;
import org.mockito.InOrder;

import tf.dpi.chunking.LogChunkingService;
import tf.dpi.embedding.EmbeddingService;
import tf.dpi.masking.LogMaskingService;
import tf.dpi.model.LogChunk;
import tf.dpi.model.LogEntry;

class LogIngestionServiceTest {

    @Test
    void overlapsMaskingWithChunkingAndUpdatesCheckpointAfterSaving() throws Exception {
        ElasticsearchLogReader reader = mock(ElasticsearchLogReader.class);
        LogMaskingService masking = mock(LogMaskingService.class);
        LogChunkingService chunking = mock(LogChunkingService.class);
        EmbeddingService embedding = mock(EmbeddingService.class);
        CheckpointService checkpoint = mock(CheckpointService.class);
        Instant firstTime = Instant.parse("2026-11-17T06:00:00Z");
        Instant secondTime = firstTime.plusSeconds(1);
        LogEntry first = entry("service-1", firstTime);
        LogEntry second = entry("service-2", secondTime);
        when(reader.readLogsAfter(null)).thenReturn(List.of(first, second));
        AtomicBoolean firstBatchIsChunking = new AtomicBoolean();
        CountDownLatch overlapObserved = new CountDownLatch(1);
        CountDownLatch firstBatchChunking = new CountDownLatch(1);

        doAnswer(invocation -> {
            List<LogEntry> batch = invocation.getArgument(0);
            if (batch.contains(second)) {
                if (!firstBatchChunking.await(5, TimeUnit.SECONDS)) {
                    throw new AssertionError("Chunking did not start before the next batch was masked.");
                }
                if (firstBatchIsChunking.get()) {
                    overlapObserved.countDown();
                }
            }
            return batch;
        }).when(masking).mask(anyList());
        doAnswer(invocation -> {
            List<LogEntry> batch = invocation.getArgument(0);
            if (batch.contains(first)) {
                firstBatchIsChunking.set(true);
                firstBatchChunking.countDown();
                if (!overlapObserved.await(5, TimeUnit.SECONDS)) {
                    throw new AssertionError("The next batch was not masked while chunking was active.");
                }
                firstBatchIsChunking.set(false);
            }
            return List.of(new LogChunk(null, "service", batch.get(0).getTimestamp(), "chunk"));
        }).when(chunking).chunk(anyList());
        doAnswer(invocation -> invocation.getArgument(0))
                .when(chunking).storeChunks(anyList());

        LogIngestionService service =
                new LogIngestionService(reader, masking, chunking, embedding, checkpoint, 1, 2);

        service.ingestNewLogs();

        assertEquals(0, firstBatchChunking.getCount());
        verify(checkpoint).update(secondTime);
        verify(embedding, org.mockito.Mockito.times(2)).embedBatch(anyList());
        InOrder saveBeforeCheckpoint = inOrder(chunking, checkpoint);
        saveBeforeCheckpoint.verify(chunking, org.mockito.Mockito.atLeastOnce()).storeChunks(anyList());
        saveBeforeCheckpoint.verify(checkpoint).update(secondTime);
    }

    @Test
    void overlapsEmbeddingWithSavingTheNextBatch() throws Exception {
        ElasticsearchLogReader reader = mock(ElasticsearchLogReader.class);
        LogMaskingService masking = mock(LogMaskingService.class);
        LogChunkingService chunking = mock(LogChunkingService.class);
        EmbeddingService embedding = mock(EmbeddingService.class);
        CheckpointService checkpoint = mock(CheckpointService.class);
        Instant firstTime = Instant.parse("2026-11-17T06:00:00Z");
        LogEntry first = entry("service-1", firstTime);
        LogEntry second = entry("service-2", firstTime.plusSeconds(1));
        when(reader.readLogsAfter(null)).thenReturn(List.of(first, second));
        CountDownLatch firstBatchEmbedding = new CountDownLatch(1);
        CountDownLatch nextBatchStored = new CountDownLatch(1);
        AtomicBoolean overlapObserved = new AtomicBoolean();

        when(masking.mask(anyList())).thenAnswer(invocation -> invocation.getArgument(0));
        when(chunking.chunk(anyList())).thenAnswer(invocation -> {
            List<LogEntry> batch = invocation.getArgument(0);
            return List.of(new LogChunk(null, batch.get(0).getServiceName(),
                    batch.get(0).getTimestamp(), batch.get(0).getMessage()));
        });
        doAnswer(invocation -> {
            List<LogChunk> batch = invocation.getArgument(0);
            if ("service-2 message".equals(batch.get(0).getContent())) {
                if (!firstBatchEmbedding.await(5, TimeUnit.SECONDS)) {
                    throw new AssertionError("The first batch did not start embedding before the next save.");
                }
                overlapObserved.set(true);
                nextBatchStored.countDown();
            }
            return batch;
        }).when(chunking).storeChunks(anyList());
        doAnswer(invocation -> {
            List<LogChunk> batch = invocation.getArgument(0);
            if ("service-1 message".equals(batch.get(0).getContent())) {
                firstBatchEmbedding.countDown();
                if (!nextBatchStored.await(5, TimeUnit.SECONDS)) {
                    throw new AssertionError("The next chunk batch was not stored during embedding.");
                }
            }
            return batch;
        }).when(embedding).embedBatch(anyList());

        LogIngestionService service =
                new LogIngestionService(reader, masking, chunking, embedding, checkpoint, 1, 2);

        service.ingestNewLogs();

        org.junit.jupiter.api.Assertions.assertTrue(overlapObserved.get());
        verify(checkpoint).update(second.getTimestamp());
    }

    @Test
    void splitsOversizedTraceGroupsIntoConfiguredBatchSize() {
        ElasticsearchLogReader reader = mock(ElasticsearchLogReader.class);
        LogMaskingService masking = mock(LogMaskingService.class);
        LogChunkingService chunking = mock(LogChunkingService.class);
        EmbeddingService embedding = mock(EmbeddingService.class);
        CheckpointService checkpoint = mock(CheckpointService.class);
        List<LogEntry> entries = java.util.stream.IntStream.range(0, 1_201)
                .mapToObj(index -> new LogEntry("same-trace", null, "same-service", "INFO",
                        Instant.EPOCH.plusSeconds(index), "entry-" + index, null, null, Map.of()))
                .toList();
        when(reader.readLogsAfter(null)).thenReturn(entries);
        when(masking.mask(anyList())).thenAnswer(invocation -> invocation.getArgument(0));
        when(chunking.chunk(anyList())).thenReturn(List.of());
        doAnswer(invocation -> invocation.getArgument(0))
                .when(chunking).storeChunks(anyList());

        LogIngestionService service =
                new LogIngestionService(reader, masking, chunking, embedding, checkpoint, 500, 3);

        service.ingestNewLogs();

        org.mockito.ArgumentCaptor<List<LogEntry>> batches =
                org.mockito.ArgumentCaptor.forClass(List.class);
        verify(masking, org.mockito.Mockito.times(3)).mask(batches.capture());
        assertEquals(List.of(500, 500, 201),
                batches.getAllValues().stream().map(List::size).toList());
    }

    @Test
    void doesNotAdvanceCheckpointWhenChunkPersistenceFails() {
        ElasticsearchLogReader reader = mock(ElasticsearchLogReader.class);
        LogMaskingService masking = mock(LogMaskingService.class);
        LogChunkingService chunking = mock(LogChunkingService.class);
        EmbeddingService embedding = mock(EmbeddingService.class);
        CheckpointService checkpoint = mock(CheckpointService.class);
        Instant timestamp = Instant.parse("2026-11-17T06:00:00Z");
        when(reader.readLogsAfter(null)).thenReturn(List.of(entry("service", timestamp)));
        when(masking.mask(anyList())).thenAnswer(invocation -> invocation.getArgument(0));
        when(chunking.chunk(anyList())).thenReturn(List.of(
                new LogChunk(null, "service", timestamp, "chunk")));
        doThrow(new IllegalStateException("database unavailable"))
                .when(chunking).storeChunks(anyList());

        LogIngestionService service =
                new LogIngestionService(reader, masking, chunking, embedding, checkpoint, 10, 1);

        assertThrows(IllegalStateException.class, service::ingestNewLogs);

        verify(checkpoint, never()).update(timestamp);
        verify(embedding, never()).persistEmbeddings(anyList());
    }

    private static LogEntry entry(String service, Instant timestamp) {
        return new LogEntry(null, null, service, "INFO", timestamp,
                service + " message", null, null, Map.of());
    }
}
