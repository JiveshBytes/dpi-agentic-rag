package tf.dpi.chunking;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import tf.dpi.model.LogChunk;
import tf.dpi.model.LogEntry;
import tf.dpi.storage.LogChunkRepository;

class LogChunkingServiceTest {

    @Test
    void flushesGeneratedChunksToRepository() {
        LogChunkRepository repository = mock(LogChunkRepository.class);
        LogChunkingService service = new LogChunkingService(
                new MetadataBasedSegmenter(),
                new TraceBasedCorrelator(),
                new TimeWindowGrouper(),
                new StackTraceProcessor(new StructureAwareChunker(512, "test", "mosip-logs")),
                repository);
        Instant timestamp = Instant.parse("2026-11-17T06:00:00Z");

        service.chunkAndStore(List.of(
                new LogEntry("trace-1", null, "gateway", "ERROR", timestamp,
                        "request failed", null, null, Map.of())));

        ArgumentCaptor<List<LogChunk>> chunks = ArgumentCaptor.forClass(List.class);
        verify(repository).saveAllAndFlush(chunks.capture());
        assertEquals(1, chunks.getValue().size());
        LogChunk storedChunk = chunks.getValue().get(0);
        org.junit.jupiter.api.Assertions.assertTrue(storedChunk.getChunkText().endsWith("request failed"));
        assertEquals("mosip-logs", storedChunk.getSourceIndex());
    }

    @Test
    void doesNotCallRepositoryForEmptyInput() {
        LogChunkRepository repository = mock(LogChunkRepository.class);
        LogChunkingService service = new LogChunkingService(
                new MetadataBasedSegmenter(),
                new TraceBasedCorrelator(),
                new TimeWindowGrouper(),
                new StackTraceProcessor(new StructureAwareChunker(512, "test", "mosip-logs")),
                repository);

        service.chunkAndStore(List.of());

        verify(repository, never()).saveAllAndFlush(anyList());
    }
}
