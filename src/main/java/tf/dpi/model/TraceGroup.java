package tf.dpi.model;

import java.util.List;

/**
 * Group of log segments correlated by a single distributed trace id.
 */
public class TraceGroup {

    private String traceId;
    private List<LogSegment> segments;

    public TraceGroup() {}

    public TraceGroup(String traceId, List<LogSegment> segments) {
        this.traceId = traceId;
        this.segments = segments;
    }

    public String getTraceId() { return traceId; }
    public void setTraceId(String traceId) { this.traceId = traceId; }
    public List<LogSegment> getSegments() { return segments; }
    public void setSegments(List<LogSegment> segments) { this.segments = segments; }
}
