package tf.dpi.chunking;

import java.util.Comparator;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Component;

import tf.dpi.model.LogEntry;
import tf.dpi.model.LogSegment;

/**
 * Segments log entries by service and trace id.
 * Prevents logs from unrelated services from being mixed while maintaining
 * chronological ordering and preserving original metadata for cross-service correlation.
 */
@Component
public class MetadataBasedSegmenter {

    public List<LogSegment> segment(List<LogEntry> entries) {
        if (entries == null || entries.isEmpty()) {
            return List.of();
        }

        Map<SegmentKey, List<LogEntry>> groups = new LinkedHashMap<>();
        for (LogEntry entry : entries) {
            if (entry == null) {
                continue;
            }
            SegmentKey key = new SegmentKey(
                    entry.getServiceName() == null ? "unknown" : entry.getServiceName(),
                    entry.getTraceId());
            groups.computeIfAbsent(key, ignored -> new ArrayList<>()).add(entry);
        }

        return groups.entrySet().stream()
                .map(group -> {
                    List<LogEntry> sortedEntries = group.getValue().stream()
                            .sorted(Comparator.comparing(LogEntry::getTimestamp,
                                    Comparator.nullsLast(Comparator.naturalOrder())))
                            .toList();
                    return new LogSegment(group.getKey().serviceName, group.getKey().traceId, sortedEntries);
                })
                .toList();
    }

    private record SegmentKey(String serviceName, String traceId) {}
}
