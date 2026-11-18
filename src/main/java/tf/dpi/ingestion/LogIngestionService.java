package tf.dpi.ingestion;

import java.time.Instant;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import tf.dpi.chunking.LogChunkingService;
import tf.dpi.embedding.EmbeddingService;
import tf.dpi.masking.LogMaskingService;
import tf.dpi.model.LogChunk;
import tf.dpi.model.LogEntry;

/**
 * Runs a bounded masking, chunking, and embedding pipeline for ingested logs.
 */
@Service
public class LogIngestionService {

    private static final Logger log = LoggerFactory.getLogger(LogIngestionService.class);

    private final ElasticsearchLogReader logReader;
    private final LogMaskingService maskingService;
    private final LogChunkingService chunkingService;
    private final EmbeddingService embeddingService;
    private final CheckpointService checkpointService;
    private final int batchSize;
    private final int maxInFlightBatches;

    @Autowired
    public LogIngestionService(ElasticsearchLogReader logReader,
                               LogMaskingService maskingService,
                               LogChunkingService chunkingService,
                               EmbeddingService embeddingService,
                               CheckpointService checkpointService,
                               @Value("${mosip.ingestion.batch-size:500}") int batchSize,
                               @Value("${mosip.ingestion.pipeline-max-in-flight:3}") int maxInFlightBatches) {
        if (batchSize < 1 || maxInFlightBatches < 1) {
            throw new IllegalArgumentException("Ingestion batch size and pipeline capacity must be positive.");
        }
        this.logReader = logReader;
        this.maskingService = maskingService;
        this.chunkingService = chunkingService;
        this.embeddingService = embeddingService;
        this.checkpointService = checkpointService;
        this.batchSize = batchSize;
        this.maxInFlightBatches = maxInFlightBatches;
    }

    public void ingestNewLogs() {
        log.info("Ingesting new logs since last checkpoint");
        var checkpoint = checkpointService.getLastTimestamp();
        var logs = logReader.readLogsAfter(checkpoint);
        log.info("Read {} raw logs from Elasticsearch", logs.size());

        if (logs.isEmpty()) {
            return;
        }

        Instant latestTimestamp = logs.stream()
                .map(LogEntry::getTimestamp)
                .filter(timestamp -> timestamp != null)
                .max(Instant::compareTo)
                .orElse(null);

        List<List<LogEntry>> batches = createTraceSafeBatches(logs);
        log.info("Processing {} log entries in {} bounded pipeline batches",
                logs.size(), batches.size());
        runPipeline(batches);
        if (latestTimestamp != null) {
            checkpointService.update(latestTimestamp);
        }
    }

    private void runPipeline(List<List<LogEntry>> batches) {
        ExecutorService maskingExecutor = Executors.newSingleThreadExecutor();
        ExecutorService chunkingExecutor = Executors.newSingleThreadExecutor();
        ExecutorService storageExecutor = Executors.newSingleThreadExecutor();
        ExecutorService embeddingExecutor = Executors.newSingleThreadExecutor();
        ArrayDeque<CompletableFuture<List<LogChunk>>> inFlight = new ArrayDeque<>();
        boolean succeeded = false;
        try {
            for (int batchIndex = 0; batchIndex < batches.size(); batchIndex++) {
                List<LogEntry> batch = batches.get(batchIndex);
                int batchNumber = batchIndex + 1;
                CompletableFuture<List<LogChunk>> future = CompletableFuture
                        .supplyAsync(() -> {
                            List<LogEntry> masked = maskingService.mask(batch);
                            log.info("Pipeline batch {}/{} masked {} entries",
                                    batchNumber, batches.size(), masked.size());
                            return masked;
                        }, maskingExecutor)
                        .thenApplyAsync(masked -> {
                            List<LogChunk> chunks = chunkingService.chunk(masked);
                            log.info("Pipeline batch {}/{} produced {} chunks",
                                    batchNumber, batches.size(), chunks.size());
                            return chunks;
                        }, chunkingExecutor)
                        .thenApplyAsync(chunks -> {
                            List<LogChunk> saved = chunkingService.storeChunks(chunks);
                            log.info("Pipeline batch {}/{} saved {} chunks",
                                    batchNumber, batches.size(), saved.size());
                            return saved;
                        }, storageExecutor)
                        .thenApplyAsync(saved -> embedSavedChunks(saved, batchNumber, batches.size()),
                                embeddingExecutor);
                inFlight.addLast(future);
                if (inFlight.size() >= maxInFlightBatches) {
                    await(inFlight.removeFirst());
                }
            }
            while (!inFlight.isEmpty()) {
                await(inFlight.removeFirst());
            }
            succeeded = true;
        } catch (RuntimeException ex) {
            inFlight.forEach(future -> future.cancel(true));
            throw ex;
        } finally {
            shutdown(maskingExecutor, succeeded);
            shutdown(chunkingExecutor, succeeded);
            shutdown(storageExecutor, succeeded);
            shutdown(embeddingExecutor, succeeded);
        }
    }

    private List<LogChunk> embedSavedChunks(List<LogChunk> savedChunks, int batchNumber, int totalBatches) {
        if (savedChunks.isEmpty()) {
            return savedChunks;
        }
        try {
            embeddingService.embedBatch(savedChunks);
            log.info("Pipeline batch {}/{} embedded and stored {} chunks",
                    batchNumber, totalBatches, savedChunks.size());
        } catch (RuntimeException ex) {
            log.error("Pipeline batch {}/{} saved {} chunks, but embedding failed. "
                    + "The scheduled embedding job will retry unembedded chunks.",
                    batchNumber, totalBatches, savedChunks.size(), ex);
        }
        return savedChunks;
    }

    private static List<LogChunk> await(CompletableFuture<List<LogChunk>> future) {
        try {
            return future.join();
        } catch (CompletionException ex) {
            Throwable cause = ex.getCause();
            if (cause instanceof RuntimeException runtimeException) {
                throw runtimeException;
            }
            throw new IllegalStateException("A log ingestion pipeline stage failed.", cause);
        }
    }

    private static void shutdown(ExecutorService executor, boolean succeeded) {
        if (succeeded) {
            executor.shutdown();
        } else {
            executor.shutdownNow();
        }
        try {
            if (!executor.awaitTermination(60, java.util.concurrent.TimeUnit.SECONDS)) {
                log.error("An ingestion pipeline executor did not stop within 60 seconds.");
            }
        } catch (InterruptedException ex) {
            executor.shutdownNow();
            Thread.currentThread().interrupt();
            log.error("Interrupted while stopping an ingestion pipeline executor.", ex);
        }
    }

    private List<List<LogEntry>> createTraceSafeBatches(List<LogEntry> logs) {
        Map<BatchKey, List<LogEntry>> groups = new LinkedHashMap<>();
        for (LogEntry entry : logs) {
            String traceId = entry.getTraceId();
            BatchKey key = traceId == null || traceId.isBlank()
                    ? new BatchKey(null, entry.getServiceName() == null ? "unknown" : entry.getServiceName())
                    : new BatchKey(traceId, null);
            groups.computeIfAbsent(key, ignored -> new ArrayList<>()).add(entry);
        }

        List<List<LogEntry>> batches = new ArrayList<>();
        List<LogEntry> current = new ArrayList<>(batchSize);
        for (List<LogEntry> group : groups.values()) {
            int offset = 0;
            while (offset < group.size()) {
                int available = batchSize - current.size();
                int count = Math.min(available, group.size() - offset);
                current.addAll(group.subList(offset, offset + count));
                offset += count;
                if (current.size() == batchSize) {
                    batches.add(List.copyOf(current));
                    current.clear();
                }
            }
        }
        if (!current.isEmpty()) {
            batches.add(List.copyOf(current));
        }
        return batches;
    }

    private record BatchKey(String traceId, String serviceName) {}
}
