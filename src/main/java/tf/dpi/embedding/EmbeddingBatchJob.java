package tf.dpi.embedding;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import tf.dpi.storage.LogChunkRepository;

/**
 * Scheduled batch job that backfills embeddings for chunks that have not yet been vectorized.
 */
@Component
@ConditionalOnProperty(prefix = "mosip.ingestion.jobs", name = "enabled", havingValue = "true", matchIfMissing = true)
public class EmbeddingBatchJob {

    private static final Logger log = LoggerFactory.getLogger(EmbeddingBatchJob.class);

    private final EmbeddingService embeddingService;
    private final LogChunkRepository chunkRepository;
    private final int batchSize;

    @Autowired
    public EmbeddingBatchJob(EmbeddingService embeddingService,
                             LogChunkRepository chunkRepository,
                             @Value("${mosip.embedding.batch-size:64}") int batchSize) {
        if (batchSize < 1) {
            throw new IllegalArgumentException("Embedding batch size must be at least one.");
        }
        this.embeddingService = embeddingService;
        this.chunkRepository = chunkRepository;
        this.batchSize = batchSize;
    }

    @Scheduled(fixedDelayString = "${mosip.ingestion.checkpoint-interval-seconds:300000}")
    public void run() {
        log.info("Starting embedding batch job for unembedded chunks");
        int total = 0;
        while (true) {
            var batch = chunkRepository.findNextUnembeddedBatch(batchSize);
            if (batch.isEmpty()) {
                break;
            }
            int embeddedInBatch = 0;
            for (var chunk : batch) {
                try {
                    embeddingService.embedBatch(java.util.List.of(chunk));
                    embeddedInBatch++;
                    total++;
                } catch (RuntimeException ex) {
                    log.error("Failed to embed chunk {}; continuing with the rest of the batch.",
                            chunk.getId(), ex);
                }
            }
            log.info("Embedded {} chunks in this run", total);
            if (embeddedInBatch == 0) {
                log.error("No chunks were embedded in this batch; stopping to avoid retrying "
                        + "the same failed records continuously.");
                break;
            }
        }
        log.info("Embedding batch job completed");
    }
}
