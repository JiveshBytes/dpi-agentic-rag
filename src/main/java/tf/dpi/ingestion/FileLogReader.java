package tf.dpi.ingestion;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import tf.dpi.model.LogEntry;

/**
 * Reads MOSIP log lines from a flat file (e.g. {@code log.txt}) and parses them
 * into {@link LogEntry} objects. Supports Apache/Nginx combined access log format
 * as well as plain free-text log lines.
 */
@Component
public class FileLogReader {

    private static final Logger log = LoggerFactory.getLogger(FileLogReader.class);

    // Combined log format:
    // 192.168.122.226 - - [12/Nov/2026:10:29:33 +0000] "GET /path HTTP/1.1" 301 169 "-" "UA" "-"
    private static final Pattern COMBINED_LOG_PATTERN = Pattern.compile(
            "^(\\S+) \\S+ \\S+ \\[([^\\]]+)\\] \"([^\"]*)\" (\\d{3}) (\\d+|-) \"([^\"]*)\" \"([^\"]*)\" \"([^\"]*)\"$");

    private static final DateTimeFormatter LOG_TIMESTAMP_FORMATTER =
            DateTimeFormatter.ofPattern("dd/MMM/yyyy:HH:mm:ss Z");

    private final Path logFilePath;

    public FileLogReader(@Value("${mosip.ingestion.log-file-path:src/main/resources/log.txt}") String logFilePath) {
        this.logFilePath = Path.of(logFilePath);
    }

    public List<LogEntry> readAll() {
        List<LogEntry> entries = new ArrayList<>();
        if (!Files.exists(logFilePath)) {
            log.warn("Log file not found at {}", logFilePath);
            return entries;
        }

        int lineNum = 0;
        try {
            for (String line : Files.readAllLines(logFilePath)) {
                lineNum++;
                if (line == null || line.isBlank()) {
                    continue;
                }
                entries.add(parseLine(line, lineNum));
            }
            log.info("Read {} log entries from {}", entries.size(), logFilePath);
        } catch (IOException ex) {
            log.error("Failed to read log file {}", logFilePath, ex);
        }
        return entries;
    }

    private LogEntry parseLine(String line, int lineNum) {
        Matcher matcher = COMBINED_LOG_PATTERN.matcher(line);
        if (matcher.matches()) {
            String ip = matcher.group(1);
            String timestampStr = matcher.group(2);
            String request = matcher.group(3);
            String status = matcher.group(4);
            String size = matcher.group(5);
            String referer = matcher.group(6);
            String userAgent = matcher.group(7);

            Instant timestamp = parseTimestamp(timestampStr);

            String message = String.format("%s %s -> %s (%s bytes)", request, ip, status, size);

            Map<String, Object> metadata = Map.of(
                    "clientIp", ip,
                    "statusCode", status,
                    "responseSize", size,
                    "referer", referer,
                    "userAgent", userAgent,
                    "lineNumber", lineNum
            );

            return new LogEntry(
                    "trace-" + lineNum,
                    null,
                    "nginx-access",
                    "INFO",
                    timestamp,
                    message,
                    "log.txt",
                    lineNum,
                    metadata
            );
        }

        // Fallback: treat the whole raw line as the message
        return new LogEntry(
                "trace-" + lineNum,
                null,
                "unknown",
                "INFO",
                Instant.now(),
                line,
                "log.txt",
                lineNum,
                Map.of("lineNumber", lineNum)
        );
    }

    private Instant parseTimestamp(String timestampStr) {
        try {
            return Instant.from(LOG_TIMESTAMP_FORMATTER.parse(timestampStr));
        } catch (Exception ex) {
            log.debug("Could not parse timestamp '{}', using now", timestampStr);
            return Instant.now();
        }
    }
}
