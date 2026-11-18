package tf.dpi.model;

import java.util.List;

/**
 * Intermediate segment of log entries grouped by metadata before trace correlation.
 */
public class LogSegment {

    private String serviceName;
    private String traceId;
    private List<LogEntry> entries;

    public LogSegment() {}

    public LogSegment(String serviceName, String traceId, List<LogEntry> entries) {
        this.serviceName = serviceName;
        this.traceId = traceId;
        this.entries = entries;
    }

    public String getServiceName() { return serviceName; }
    public void setServiceName(String serviceName) { this.serviceName = serviceName; }
    public String getTraceId() { return traceId; }
    public void setTraceId(String traceId) { this.traceId = traceId; }
    public List<LogEntry> getEntries() { return entries; }
    public void setEntries(List<LogEntry> entries) { this.entries = entries; }
}
