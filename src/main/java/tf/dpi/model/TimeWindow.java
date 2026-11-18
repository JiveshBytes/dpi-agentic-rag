package tf.dpi.model;

import java.time.Instant;
import java.util.List;

/**
 * A time-bounded window of correlated trace logs used for chunking.
 */
public class TimeWindow {

    private Instant start;
    private Instant end;
    private List<TraceGroup> traceGroups;

    public TimeWindow() {}

    public TimeWindow(Instant start, Instant end, List<TraceGroup> traceGroups) {
        this.start = start;
        this.end = end;
        this.traceGroups = traceGroups;
    }

    public Instant getStart() { return start; }
    public void setStart(Instant start) { this.start = start; }
    public Instant getEnd() { return end; }
    public void setEnd(Instant end) { this.end = end; }
    public List<TraceGroup> getTraceGroups() { return traceGroups; }
    public void setTraceGroups(List<TraceGroup> traceGroups) { this.traceGroups = traceGroups; }
}
