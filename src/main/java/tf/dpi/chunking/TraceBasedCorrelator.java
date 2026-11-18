package tf.dpi.chunking;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Component;

import tf.dpi.model.LogSegment;
import tf.dpi.model.TraceGroup;

/**
 * Correlates log segments by trace id so that all logs belonging to the same
 * distributed trace stay together for chunking.
 */
@Component
public class TraceBasedCorrelator {

    public List<TraceGroup> correlate(List<LogSegment> segments) {
        if (segments == null || segments.isEmpty()) {
            return List.of();
        }

        Map<String, List<LogSegment>> tracedSegments = new LinkedHashMap<>();
        List<TraceGroup> groups = new ArrayList<>();
        for (LogSegment segment : segments) {
            if (segment == null) {
                continue;
            }
            String traceId = segment.getTraceId();
            if (traceId == null || traceId.isBlank()) {
                groups.add(new TraceGroup(null, List.of(segment)));
            } else {
                tracedSegments.computeIfAbsent(traceId, ignored -> new ArrayList<>()).add(segment);
            }
        }

        tracedSegments.forEach((traceId, correlated) -> groups.add(new TraceGroup(traceId, correlated)));
        return groups;
    }
}
