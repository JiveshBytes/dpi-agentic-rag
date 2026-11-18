package tf.dpi.chunking;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import tf.dpi.model.LogChunk;
import tf.dpi.model.LogEntry;
import tf.dpi.model.LogSegment;
import tf.dpi.model.TimeWindow;
import tf.dpi.model.TraceGroup;

class LogChunkingPipelineTest {

    @Test
    void segmentsAcrossLogLevelsAndSortsNullSafeByTimestamp() {
        MetadataBasedSegmenter segmenter = new MetadataBasedSegmenter();
        LogEntry later = entry("trace-1", "api", "ERROR", Instant.parse("2026-11-17T01:00:02Z"), "failed");
        LogEntry earlier = entry("trace-1", "api", "INFO", Instant.parse("2026-11-17T01:00:01Z"), "started");
        LogEntry withoutTimestamp = entry("trace-1", "api", "DEBUG", null, "detail");

        List<LogSegment> segments = segmenter.segment(List.of(later, earlier, withoutTimestamp));

        assertEquals(1, segments.size());
        assertEquals(List.of(earlier, later, withoutTimestamp), segments.get(0).getEntries());
    }

    @Test
    void correlatesServicesByTraceAndKeepsUntracedSegmentsSeparate() {
        TraceBasedCorrelator correlator = new TraceBasedCorrelator();
        LogSegment firstService = new LogSegment("api", "trace-1", List.of());
        LogSegment secondService = new LogSegment("worker", "trace-1", List.of());
        LogSegment untracedOne = new LogSegment("api", null, List.of());
        LogSegment untracedTwo = new LogSegment("worker", null, List.of());

        List<TraceGroup> groups = correlator.correlate(
                List.of(firstService, secondService, untracedOne, untracedTwo));

        assertEquals(3, groups.size());
        assertEquals(List.of(firstService, secondService), groups.stream()
                .filter(group -> "trace-1".equals(group.getTraceId()))
                .findFirst()
                .orElseThrow()
                .getSegments());
        assertEquals(2, groups.stream().filter(group -> group.getTraceId() == null).count());
    }

    @Test
    void fullPipelineProducesChunksWithoutSplittingStackTrace() {
        Instant start = Instant.parse("2026-11-17T01:00:00Z");
        List<LogEntry> entries = List.of(
                entry("trace-1", "api", "INFO", start, "request processing started"),
                entry("trace-1", "api", "ERROR", start.plusSeconds(1),
                        "java.lang.IllegalStateException: request failed"),
                entry("trace-1", "api", "ERROR", start.plusSeconds(2),
                        "    at com.example.Api.handle(Api.java:42)"),
                entry("trace-1", "api", "INFO", start.plusSeconds(3), "request processing finished"));
        MetadataBasedSegmenter segmenter = new MetadataBasedSegmenter();
        TraceBasedCorrelator correlator = new TraceBasedCorrelator();
        TimeWindowGrouper grouper = new TimeWindowGrouper();
        StackTraceProcessor processor = new StackTraceProcessor(new StructureAwareChunker(8, "test"));

        List<LogSegment> segments = segmenter.segment(entries);
        List<TraceGroup> traceGroups = correlator.correlate(segments);
        List<TimeWindow> windows = grouper.group(traceGroups);
        List<LogChunk> chunks = processor.process(windows);

        assertFalse(chunks.isEmpty());
        LogChunk stackTraceChunk = chunks.stream()
                .filter(chunk -> chunk.getChunkText().contains("IllegalStateException"))
                .findFirst()
                .orElseThrow();
        assertTrue(stackTraceChunk.getChunkText().contains("at com.example.Api.handle(Api.java:42)"));
        assertEquals("trace-1", stackTraceChunk.getTraceId());
        assertEquals("api", stackTraceChunk.getServiceName());
        assertEquals(start.plusSeconds(1), stackTraceChunk.getTimestampStart());
        assertEquals(start.plusSeconds(2), stackTraceChunk.getTimestampEnd());
        assertEquals("java.lang.IllegalStateException", stackTraceChunk.getErrorType());
        assertEquals("STACK_TRACE", stackTraceChunk.getChunkType());
    }

    @Test
    void assignsDatabaseAllowedTypesForNormalAndErrorChunks() {
        Instant timestamp = Instant.parse("2026-11-17T01:00:00Z");
        StructureAwareChunker chunker = new StructureAwareChunker(512, "test");

        LogChunk normal = chunker.chunkSegment(new LogSegment("api", "trace-normal",
                List.of(entry("trace-normal", "api", "INFO", timestamp, "request started")))).get(0);
        LogChunk error = chunker.chunkSegment(new LogSegment("api", "trace-error",
                List.of(entry("trace-error", "api", "ERROR", timestamp,
                        "java.lang.IllegalStateException: request failed")))).get(0);

        assertEquals("NORMAL", normal.getChunkType());
        assertEquals("ERROR", error.getChunkType());
    }

    private static LogEntry entry(String traceId, String service, String level, Instant timestamp, String message) {
        return new LogEntry(traceId, null, service, level, timestamp, message, "test.log", null, Map.of());
    }
}
