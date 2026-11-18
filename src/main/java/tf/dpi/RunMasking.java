package tf.dpi;

import tf.dpi.ingestion.FileLogReader;
import tf.dpi.masking.LogMaskingService;
import tf.dpi.masking.MaskingConfig;
import tf.dpi.masking.SensitiveDataDetector;
import tf.dpi.model.LogEntry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.nio.file.*;
import java.time.Instant;
import java.util.List;

public class RunMasking {

    private static final Logger log = LoggerFactory.getLogger(RunMasking.class);

    public static void main(String[] args) {
        log.info("=== MOSIP DPI Masking Pipeline Started ===");
        log.info("Reading log file: src/main/resources/log.txt");

        // Create FileLogReader
        FileLogReader reader = new FileLogReader("src/main/resources/log.txt");

        // Read all log entries
        List<LogEntry> rawEntries = reader.readAll();
        log.info("Read {} raw log entries from log.txt", rawEntries.size());

        if (rawEntries.isEmpty()) {
            log.warn("No log entries found - check that log.txt exists and has content");
            System.exit(0);
        }

        // Create LogMaskingService
        // Note: In standalone mode, we use the default masking rules configured in application.yml
        LogMaskingService maskingService = new LogMaskingService(
                new SensitiveDataDetector(), new MaskingConfig());

        // Mask all entries
        log.info("Starting masking process...");
        List<LogEntry> maskedEntries = maskingService.mask(rawEntries);
        log.info("Masking complete. {} entries masked", maskedEntries.size());

        // Save to file
        String outputPath = "src/main/resources/masked_logs.txt";
        try {
            Path path = Paths.get(outputPath);
            Files.createDirectories(path.getParent());

            try (BufferedWriter writer = Files.newBufferedWriter(path, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING)) {
                writer.write("MOSIP DPI Masked Log Output");
                writer.newLine();
                writer.write("Generated: " + Instant.now());
                writer.newLine();
                writer.write("Total masked entries: " + maskedEntries.size());
                writer.newLine();
                writer.newLine();

                for (int i = 0; i < maskedEntries.size(); i++) {
                    LogEntry entry = maskedEntries.get(i);
                    writer.write(String.format("%d. [%s] %s | %s | %s",
                            i + 1,
                            entry.getTimestamp(),
                            entry.getServiceName(),
                            entry.getLogLevel(),
                            entry.getMessage()));
                    writer.newLine();
                }
            }
            log.info("✅ Masked entries saved to: {}", outputPath);
        } catch (IOException e) {
            log.error("❌ Failed to write output file: {}", outputPath, e);
            System.exit(1);
        }

        // Log sample to console
        int sampleSize = Math.min(5, maskedEntries.size());
        log.info("=== Sample of masked log entries (first 5) ===");
        for (int i = 0; i < sampleSize; i++) {
            LogEntry entry = maskedEntries.get(i);
            log.info("  {}: [{}] {} | {} | {}", i + 1, entry.getTimestamp(), entry.getServiceName(), entry.getLogLevel(), entry.getMessage());
        }
        log.info("=== End of sample ===");

        log.info("=== MOSIP DPI Masking Pipeline Completed Successfully ===");
        System.exit(0);
    }
}