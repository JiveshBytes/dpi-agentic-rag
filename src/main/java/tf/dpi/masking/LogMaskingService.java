package tf.dpi.masking;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import tf.dpi.model.LogEntry;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Service that scans log entries for sensitive data and applies configured masking rules
 */
@Service
public class LogMaskingService {

    private static final Logger log = LoggerFactory.getLogger(LogMaskingService.class);

    private final SensitiveDataDetector detector;
    private final MaskingConfig config;
    private final int configuredParallelism;

    @Autowired
    public LogMaskingService(
            SensitiveDataDetector detector,
            MaskingConfig config,
            @Value("${mosip.masking.parallelism:0}") int configuredParallelism) {
        this.detector = detector;
        this.config = config;
        if (configuredParallelism < 0) {
            throw new IllegalArgumentException("Masking parallelism cannot be negative.");
        }
        this.configuredParallelism = configuredParallelism;
    }

    public LogMaskingService(SensitiveDataDetector detector, MaskingConfig config) {
        this(detector, config, 0);
    }

    public List<LogEntry> mask(List<LogEntry> logs) {
        if (logs == null) {
            throw new IllegalArgumentException("Log entries must not be null.");
        }
        log.info("Masking {} log entries", logs.size());
        if (logs.isEmpty()) {
            return List.of();
        }

        long startNanos = System.nanoTime();
        int parallelism = configuredParallelism == 0
                ? Math.min(8, Runtime.getRuntime().availableProcessors())
                : configuredParallelism;
        int workerCount = Math.min(Math.max(1, parallelism), logs.size());
        log.info("Masking with {} worker thread(s)", workerCount);

        if (workerCount == 1) {
            return maskRange(logs, 0, logs.size(), new AtomicInteger(), startNanos);
        }

        ExecutorService executor = Executors.newFixedThreadPool(workerCount);
        try {
            int batchSize = (logs.size() + workerCount - 1) / workerCount;
            AtomicInteger processedCount = new AtomicInteger();
            List<Future<List<LogEntry>>> futures = new ArrayList<>(workerCount);
            for (int worker = 0; worker < workerCount; worker++) {
                int start = worker * batchSize;
                int end = Math.min(start + batchSize, logs.size());
                if (start < end) {
                    futures.add(executor.submit(
                            () -> maskRange(logs, start, end, processedCount, startNanos)));
                }
            }

            List<LogEntry> masked = new ArrayList<>(logs.size());
            for (Future<List<LogEntry>> future : futures) {
                masked.addAll(future.get());
            }
            return List.copyOf(masked);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while masking log entries.", ex);
        } catch (ExecutionException ex) {
            Throwable cause = ex.getCause();
            if (cause instanceof RuntimeException runtimeException) {
                throw runtimeException;
            }
            throw new IllegalStateException("Failed while masking log entries.", cause);
        } finally {
            executor.shutdownNow();
        }
    }

    private List<LogEntry> maskRange(
            List<LogEntry> logs, int start, int end, AtomicInteger processedCount, long startNanos) {
        List<LogEntry> masked = new ArrayList<>(end - start);
        for (int i = start; i < end; i++) {
            masked.add(maskEntry(logs.get(i)));
            int processed = processedCount.incrementAndGet();
            if (processed % 1000 == 0 || processed == logs.size()) {
                long elapsedMillis = (System.nanoTime() - startNanos) / 1_000_000;
                log.info("Masked {}/{} log entries in {} ms", processed, logs.size(), elapsedMillis);
            }
        }
        return masked;
    }

    /**
     * Masks sensitive data in a raw text string
     */
    public String mask(String text) {
        if (text == null || text.isBlank()) {
            return text;
        }
        String masked = text;
        for (MaskingRule rule : config.getRules()) {
            masked = detector.mask(masked, rule);
        }
        return masked;
    }

    private LogEntry maskEntry(LogEntry entry) {
        var message = entry.getMessage();
        for (MaskingRule rule : config.getRules()) {
            message = detector.mask(message, rule);
        }
        return new LogEntry(
                entry.getTraceId(),
                entry.getSpanId(),
                entry.getServiceName(),
                entry.getLogLevel(),
                entry.getTimestamp(),
                message,
                entry.getSourceFile(),
                entry.getLineNumber(),
                entry.getMetadata()
        );
    }
}
