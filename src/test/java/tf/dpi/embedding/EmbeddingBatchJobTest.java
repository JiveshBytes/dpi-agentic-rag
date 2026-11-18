package tf.dpi.embedding;

import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.Test;

import tf.dpi.model.LogChunk;
import tf.dpi.storage.LogChunkRepository;

class EmbeddingBatchJobTest {

    @Test
    void continuesAfterOneChunkFailsAndProcessesRemainingChunksIndividually() {
        EmbeddingService embeddingService = mock(EmbeddingService.class);
        LogChunkRepository repository = mock(LogChunkRepository.class);
        LogChunk failedChunk = new LogChunk("trace-1", "service", Instant.EPOCH, "fails");
        LogChunk successfulChunk = new LogChunk("trace-2", "service", Instant.EPOCH, "succeeds");
        when(repository.findNextUnembeddedBatch(anyInt()))
                .thenReturn(List.of(failedChunk, successfulChunk))
                .thenReturn(List.of());
        doAnswer(invocation -> {
            List<LogChunk> chunks = invocation.getArgument(0);
            if (chunks.get(0) == failedChunk) {
                throw new IllegalStateException("temporary model error");
            }
            return chunks;
        }).when(embeddingService).embedBatch(org.mockito.ArgumentMatchers.anyList());

        new EmbeddingBatchJob(embeddingService, repository, 64).run();

        verify(embeddingService).embedBatch(List.of(failedChunk));
        verify(embeddingService).embedBatch(List.of(successfulChunk));
        verify(repository, org.mockito.Mockito.times(2)).findNextUnembeddedBatch(64);
    }
}
