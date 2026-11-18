package tf.dpi.model;

import java.time.Instant;
import java.util.Map;

/**
 * Immutable representation of a raw log entry ingested from Elasticsearch.
 */
public class LogEntry {

    private final String traceId;
    private final String spanId;
    private final String serviceName;
    private final String logLevel;
    private final Instant timestamp;
    private final String message;
    private final String sourceFile;
    private final Integer lineNumber;
    private final Map<String, Object> metadata;

    public LogEntry(String traceId, String spanId, String serviceName, String logLevel,
                    Instant timestamp, String message, String sourceFile,
                    Integer lineNumber, Map<String, Object> metadata) {
        this.traceId = traceId;
        this.spanId = spanId;
        this.serviceName = serviceName;
        this.logLevel = logLevel;
        this.timestamp = timestamp;
        this.message = message;
        this.sourceFile = sourceFile;
        this.lineNumber = lineNumber;
        this.metadata = metadata;
    }

    public String getTraceId() { return traceId; }
    public String getSpanId() { return spanId; }
    public String getServiceName() { return serviceName; }
    public String getLogLevel() { return logLevel; }
    public Instant getTimestamp() { return timestamp; }
    public String getMessage() { return message; }
    public String getSourceFile() { return sourceFile; }
    public Integer getLineNumber() { return lineNumber; }
    public Map<String, Object> getMetadata() { return metadata; }
}
