package tf.dpi.chunking;

import java.util.ArrayList;
import java.util.List;

import org.springframework.stereotype.Component;

import tf.dpi.model.LogChunk;
import tf.dpi.model.LogSegment;
import tf.dpi.model.TraceGroup;
import tf.dpi.model.TimeWindow;

/**
 * Detects and preserves Java stack traces across chunk boundaries so that a
 * stack trace is never split into two different chunks.
 */
@Component
public class StackTraceProcessor {

    private final StructureAwareChunker chunker;

    public StackTraceProcessor(StructureAwareChunker chunker) {
        this.chunker = chunker;
    }

    public List<LogChunk> process(List<TimeWindow> windows) {
        if (windows == null || windows.isEmpty()) {
            return List.of();
        }

        List<LogChunk> chunks = new ArrayList<>();
        for (TimeWindow window : windows) {
            if (window == null || window.getTraceGroups() == null) {
                continue;
            }
            for (TraceGroup traceGroup : window.getTraceGroups()) {
                if (traceGroup == null || traceGroup.getSegments() == null) {
                    continue;
                }
                for (LogSegment segment : traceGroup.getSegments()) {
                    chunks.addAll(chunker.chunkSegment(segment));
                }
            }
        }
        return chunks;
    }
}
