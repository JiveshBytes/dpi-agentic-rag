package tf.dpi.chunking;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import org.springframework.stereotype.Component;

import tf.dpi.model.TraceGroup;
import tf.dpi.model.TimeWindow;

/**
 * Groups correlated trace logs into fixed or sliding time windows for bounded chunking.
 */
@Component
public class TimeWindowGrouper {

    private final Duration windowSize;

    public TimeWindowGrouper() {
        this.windowSize = Duration.ofMinutes(15);
    }

    public List<TimeWindow> group(List<TraceGroup> traceGroups) {
        if (traceGroups == null || traceGroups.isEmpty()) {
            return List.of();
        }

        long windowSeconds = windowSize.getSeconds();
        Map<Long, List<TraceGroup>> buckets = new TreeMap<>();
        for (TraceGroup traceGroup : traceGroups) {
            if (traceGroup == null || traceGroup.getSegments() == null) {
                continue;
            }
            Instant firstTimestamp = traceGroup.getSegments().stream()
                    .filter(segment -> segment != null && segment.getEntries() != null)
                    .flatMap(segment -> segment.getEntries().stream())
                    .filter(entry -> entry != null && entry.getTimestamp() != null)
                    .map(entry -> entry.getTimestamp())
                    .min(Comparator.naturalOrder())
                    .orElse(null);
            long bucket = firstTimestamp == null
                    ? Long.MIN_VALUE
                    : Math.floorDiv(firstTimestamp.getEpochSecond(), windowSeconds);
            buckets.computeIfAbsent(bucket, ignored -> new ArrayList<>()).add(traceGroup);
        }

        List<TimeWindow> windows = new ArrayList<>(buckets.size());
        buckets.forEach((bucket, groups) -> {
            if (bucket == Long.MIN_VALUE) {
                windows.add(new TimeWindow(null, null, groups));
            } else {
                Instant start = Instant.ofEpochSecond(bucket * windowSeconds);
                windows.add(new TimeWindow(start, start.plus(windowSize), groups));
            }
        });
        return windows;
    }
}
