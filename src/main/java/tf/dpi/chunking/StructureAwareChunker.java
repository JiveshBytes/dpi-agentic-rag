package tf.dpi.chunking;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.regex.Pattern;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import tf.dpi.model.LogChunk;
import tf.dpi.model.LogEntry;
import tf.dpi.model.LogSegment;

@Component
public class StructureAwareChunker {

    private static final Pattern STACK_TRACE_LINE =
            Pattern.compile("^\\s+(at\\s+|\\.\\.\\.\\s+\\d+\\s+more\\b)", Pattern.MULTILINE);
    private static final Pattern EXCEPTION_LINE =
            Pattern.compile("(?m)^\\s*(?:Caused by:\\s*)?[\\w.$]*(?:Exception|Error)(?::|\\s|$)");

    private final int maxTokens;
    private final String stage;
    private final String sourceIndex;

    @Autowired
    public StructureAwareChunker(
            @Value("${mosip.chunking.max-chunk-size:512}") int maxTokens,
            @Value("${mosip.stage:local}") String stage,
            @Value("${mosip.elasticsearch.index:niraqa1.ida.auth_transaction}") String sourceIndex) {
        if (maxTokens < 1) {
            throw new IllegalArgumentException("Maximum chunk size must be at least one token.");
        }
        this.maxTokens = maxTokens;
        this.stage = stage;
        this.sourceIndex = sourceIndex;
    }

    public StructureAwareChunker(int maxTokens, String stage) {
        this(maxTokens, stage, "niraqa1.ida.auth_transaction");
    }

    public List<LogChunk> chunkSegment(LogSegment segment) {
        if (segment == null || segment.getEntries() == null || segment.getEntries().isEmpty()) {
            return List.of();
        }

        List<LogEntry> entries = segment.getEntries().stream()
                .filter(entry -> entry != null && entry.getMessage() != null && !entry.getMessage().isBlank())
                .sorted(Comparator.comparing(LogEntry::getTimestamp,
                        Comparator.nullsLast(Comparator.naturalOrder())))
                .toList();
        List<LogChunk> chunks = new ArrayList<>();
        List<LogEntry> currentEntries = new ArrayList<>();
        StringBuilder currentText = new StringBuilder();
        int currentTokens = 0;
        boolean inStackTrace = false;

        for (LogEntry entry : entries) {
            String rendered = renderEntry(entry);
            int entryTokens = estimateTokenCount(rendered);
            boolean beginsStackTrace = isException(entry.getMessage());
            boolean stackTraceLine = isStackTrace(entry.getMessage());
            if (inStackTrace && !stackTraceLine && !beginsStackTrace) {
                inStackTrace = false;
            }

            if (!currentEntries.isEmpty() && !inStackTrace && !stackTraceLine
                    && currentTokens + entryTokens > maxTokens) {
                chunks.add(buildChunk(segment, currentEntries, currentText.toString()));
                currentEntries.clear();
                currentText.setLength(0);
                currentTokens = 0;
            }

            if (currentText.length() > 0) {
                currentText.append(System.lineSeparator());
            }
            currentText.append(rendered);
            currentEntries.add(entry);
            currentTokens += entryTokens;

            if (beginsStackTrace || stackTraceLine) {
                inStackTrace = true;
            } else if (inStackTrace) {
                inStackTrace = false;
            }
        }

        if (!currentEntries.isEmpty()) {
            chunks.add(buildChunk(segment, currentEntries, currentText.toString()));
        }
        return chunks;
    }

    private String renderEntry(LogEntry entry) {
        StringBuilder line = new StringBuilder();
        if (entry.getTimestamp() != null) {
            line.append(entry.getTimestamp()).append(' ');
        }
        if (entry.getLogLevel() != null && !entry.getLogLevel().isBlank()) {
            line.append(entry.getLogLevel()).append(' ');
        }
        return line.append(entry.getMessage().trim()).toString();
    }

    private boolean isStackTrace(String message) {
        return STACK_TRACE_LINE.matcher(message).find()
                || message.stripLeading().startsWith("Caused by:");
    }

    private boolean isException(String message) {
        return EXCEPTION_LINE.matcher(message).find();
    }

    private int estimateTokenCount(String text) {
        return Math.max(1, (text.length() + 3) / 4);
    }

    private LogChunk buildChunk(LogSegment segment, List<LogEntry> entries, String content) {
        Instant start = entries.stream()
                .map(LogEntry::getTimestamp)
                .filter(timestamp -> timestamp != null)
                .min(Comparator.naturalOrder())
                .orElse(null);
        Instant end = entries.stream()
                .map(LogEntry::getTimestamp)
                .filter(timestamp -> timestamp != null)
                .max(Comparator.naturalOrder())
                .orElse(null);

        LogChunk chunk = new LogChunk();
        chunk.setStage(stage);
        chunk.setTraceId(segment.getTraceId());
        chunk.setServiceName(segment.getServiceName());
        chunk.setTimestampStart(start);
        chunk.setTimestampEnd(end);
        chunk.setContent(content.trim());
        entries.stream()
                .map(LogEntry::getLogLevel)
                .filter(level -> level != null && !level.isBlank())
                .findFirst()
                .ifPresent(chunk::setLogLevel);
        chunk.setSourceIndex(sourceIndex);
        String errorLine = entries.stream()
                .map(LogEntry::getMessage)
                .filter(this::isException)
                .findFirst()
                .orElse(null);
        boolean hasStackTrace = entries.stream()
                .map(LogEntry::getMessage)
                .anyMatch(this::isStackTrace);
        chunk.setChunkType(hasStackTrace ? "STACK_TRACE" : errorLine != null ? "ERROR" : "NORMAL");
        if (errorLine != null) {
            String firstLine = errorLine.lines().findFirst().orElse(errorLine);
            chunk.setErrorMessage(firstLine);
            int colon = firstLine.indexOf(':');
            chunk.setErrorType(colon > 0 ? firstLine.substring(0, colon).trim() : firstLine.trim());
        }
        chunk.setChunkingVersion("1");
        return chunk;
    }
}
