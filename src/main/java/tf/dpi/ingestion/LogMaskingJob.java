package tf.dpi.ingestion;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import tf.dpi.masking.LogMaskingService;

import java.io.*;
import java.nio.file.*;
import java.util.List;

@Component
@ConditionalOnProperty(prefix = "mosip.ingestion.file-masking-job", name = "enabled", havingValue = "true")
public class LogMaskingJob {

    private static final Logger log = LoggerFactory.getLogger(LogMaskingJob.class);

    private final FileLogReader fileLogReader;
    private final LogMaskingService maskingService;

    @Value("${mosip.ingestion.log-file-path:src/main/resources/log.txt}")
    private String logFilePath;

    @Value("${mosip.ingestion.masked-output-path:src/main/resources/masked_logs.txt}")
    private String maskedOutputPath;

    @Autowired
    public LogMaskingJob(FileLogReader fileLogReader,
                         LogMaskingService maskingService) {
        this.fileLogReader = fileLogReader;
        this.maskingService = maskingService;
    }

    /**
     * Reads log.txt, masks every entry, saves results to file, and logs a sample.
     * Runs on a fixed delay so you can re-run masking whenever log.txt changes.
     */
    @Scheduled(fixedDelayString = "${mosip.ingestion.checkpoint-interval-seconds:60000}")
    public void runMasking() {
        log.info("Starting log masking job");

        List<tf.dpi.model.LogEntry> rawEntries = fileLogReader.readAll();
        if (rawEntries.isEmpty()) {
            log.warn("No log entries found to mask");
            return;
        }

        List<tf.dpi.model.LogEntry> maskedEntries = maskingService.mask(rawEntries);
        log.info("Masked {} log entries", maskedEntries.size());

        // Save masked entries to file
        saveMaskedEntriesToFile(maskedEntries);

        // Log first 5 masked entries for verification
        int sampleSize = Math.min(5, maskedEntries.size());
        log.info("=== Sample of masked log entries ===");
        for (int i = 0; i < sampleSize; i++) {
            tf.dpi.model.LogEntry entry = maskedEntries.get(i);
            log.info("[{}] {} | {} | {}", entry.getTimestamp(), entry.getServiceName(), entry.getLogLevel(), entry.getMessage());
        }
        log.info("=== End of sample ===");

        // Also log the masked raw line form for each entry (line-by-line masking)
        log.info("=== Masked raw lines (sample) ===");
        for (int i = 0; i < sampleSize; i++) {
            tf.dpi.model.LogEntry entry = maskedEntries.get(i);
            log.info(entry.getMessage());
        }
        log.info("=== End of masked raw lines ===");

        log.info("Log masking job completed");
    }

    /**
     * Saves masked log entries to a text file.
     * Creates the file if it doesn't exist, overwrites on each run.
     */
    private void saveMaskedEntriesToFile(List<tf.dpi.model.LogEntry> entries) {
        try {
            Path outputPath = Paths.get(maskedOutputPath);
            // Ensure parent directory exists
            Files.createDirectories(outputPath.getParent());

            try (BufferedWriter writer = Files.newBufferedWriter(outputPath, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING)) {
                writer.write("MOSIP DPI Masked Log Output");
                writer.newLine();
                writer.write("Generated: " + java.time.Instant.now());
                writer.newLine();
                writer.write("Total entries: " + entries.size());
                writer.newLine();
                writer.newLine();

                for (int i = 0; i < entries.size(); i++) {
                    tf.dpi.model.LogEntry entry = entries.get(i);
                    writer.write(String.format("%d. [%s] %s | %s | %s",
                            i + 1,
                            entry.getTimestamp(),
                            entry.getServiceName(),
                            entry.getLogLevel(),
                            entry.getMessage()));
                    writer.newLine();
                }
            }

            log.info("Masked entries saved to {}", maskedOutputPath);
        } catch (IOException ex) {
            log.error("Failed to save masked entries to file {}", maskedOutputPath, ex);
        }
    }
}
