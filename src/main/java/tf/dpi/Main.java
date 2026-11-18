package tf.dpi;

import tf.dpi.ingestion.FileLogReader;
import tf.dpi.masking.LogMaskingService;
import tf.dpi.masking.MaskingConfig;
import tf.dpi.masking.SensitiveDataDetector;
import tf.dpi.model.LogEntry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

public class Main {

    private static final Logger log = LoggerFactory.getLogger(Main.class);

    public static void main(String[] args) {
        log.info("Starting MOSIP DPI Masking Pipeline (standalone mode)");

        // Create components manually (no Spring Boot context needed)
        FileLogReader fileLogReader = new FileLogReader("src/main/resources/log.txt");
        LogMaskingService maskingService = new LogMaskingService(
                new SensitiveDataDetector(), new MaskingConfig());

        // Read and mask
        List<LogEntry> rawEntries = fileLogReader.readAll();
        if (rawEntries.isEmpty()) {
            log.warn("No log entries found in log.txt");
            System.exit(0);
        }

        log.info("Read {} raw log entries", rawEntries.size());

        List<LogEntry> maskedEntries = maskingService.mask(rawEntries);
        log.info("Masked {} log entries", maskedEntries.size());

        // Save to file
        String outputPath = "src/main/resources/masked_logs.txt";
        try {
            java.nio.file.Files.createDirectories(java.nio.file.Paths.get(outputPath).getParent());
            try (java.io.BufferedWriter writer = java.nio.file.Files.newBufferedWriter(java.nio.file.Paths.get(outputPath), java.nio.file.StandardOpenOption.CREATE, java.nio.file.StandardOpenOption.TRUNCATE_EXISTING)) {
                writer.write("MOSIP DPI Masked Log Output");
                writer.newLine();
                writer.write("Generated: " + java.time.Instant.now());
                writer.newLine();
                writer.write("Total entries: " + maskedEntries.size());
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
            log.info("Masked entries saved to {}", outputPath);
        } catch (Exception ex) {
            log.error("Failed to save masked entries", ex);
            System.exit(1);
        }

        // Log sample
        int sampleSize = Math.min(5, maskedEntries.size());
        log.info("=== Sample of masked log entries ===");
        for (int i = 0; i < sampleSize; i++) {
            LogEntry entry = maskedEntries.get(i);
            log.info("[{}] {} | {} | {}", entry.getTimestamp(), entry.getServiceName(), entry.getLogLevel(), entry.getMessage());
        }
        log.info("=== End of sample ===");

        log.info("MOSIP DPI Masking Pipeline completed successfully");
        System.exit(0);
    }
}