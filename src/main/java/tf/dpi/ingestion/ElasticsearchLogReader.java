package tf.dpi.ingestion;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.time.Instant;
import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import tf.dpi.model.LogEntry;

/**
 * Reader adapter that queries Elasticsearch for MOSIP log entries newer than a given point in time.
 */
@Component
public class ElasticsearchLogReader {

    private static final Logger log = LoggerFactory.getLogger(ElasticsearchLogReader.class);

    private final KibanaProxyClient kibana;
    private final ObjectMapper objectMapper;
    private final String index;
    private final String timeField;
    private final String query;
    private final int pageSize;

    public ElasticsearchLogReader(
            KibanaProxyClient kibana,
            ObjectMapper objectMapper,
            @Value("${mosip.elasticsearch.index:niraqa1.ida.auth_transaction}") String index,
            @Value("${mosip.elasticsearch.time-field:request_dtimes}") String timeField,
            @Value("${mosip.elasticsearch.query:}") String query,
            @Value("${mosip.elasticsearch.page-size:500}") int pageSize) {
        this.kibana = kibana;
        this.objectMapper = objectMapper;
        this.index = index;
        this.timeField = timeField;
        this.query = query;
        this.pageSize = pageSize;
    }

    public List<LogEntry> readLogsAfter(Instant since) {
        log.debug("Reading logs after {}", since);
        Instant start = since == null ? Instant.EPOCH : since;
        Instant end = Instant.now();
        if (!start.isBefore(end)) {
            return List.of();
        }
        try {
            List<JsonNode> sources = kibana.fetchLogs(index, timeField, start, end, query, pageSize);
            List<LogEntry> entries = sources.stream()
                    .map(this::toLogEntry)
                    .filter(entry -> entry != null)
                    .toList();
            log.info("Mapped {} Kibana documents to log entries", entries.size());
            return entries;
        } catch (IOException ex) {
            throw new IllegalStateException("Could not read logs from Kibana for index pattern " + index, ex);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while reading logs from Kibana.", ex);
        }
    }

    private LogEntry toLogEntry(JsonNode source) {
        JsonNode timestampNode = field(source, timeField, "@timestamp", "timestamp", "timestamp");
        Instant timestamp = toInstant(timestampNode);
        if (timestamp == null) {
            log.warn("Skipping Kibana log document without a parseable '{}' timestamp", timeField);
            return null;
        }

        JsonNode messageNode = field(source, "message", "log", "msg", "error.message");
        String message = text(messageNode);
        if (message == null || message.isBlank()) {
            try {
                message = objectMapper.writeValueAsString(source);
            } catch (IOException ex) {
                throw new IllegalStateException("Could not serialize a Kibana log document.", ex);
            }
        }

        Map<String, Object> metadata = objectMapper.convertValue(
                source, new TypeReference<LinkedHashMap<String, Object>>() { });
        return new LogEntry(
                text(field(source, "trace.id", "traceId", "trace_id")),
                text(field(source, "span.id", "spanId", "span_id")),
                text(field(source, "service.name", "serviceName", "appName", "app_name")),
                text(field(source, "log.level", "level", "severity", "level_name")),
                timestamp,
                message,
                text(field(source, "log.file.path", "source.file.name", "sourceFile")),
                integer(field(source, "log.file.line", "source.line", "lineNumber")),
                metadata);
    }

    private static JsonNode field(JsonNode source, String... names) {
        for (String name : names) {
            JsonNode value = source.get(name);
            if (value == null) {
                value = source;
                for (String part : name.split("\\.")) {
                    value = value == null ? null : value.get(part);
                }
            }
            if (value != null && !value.isNull()) {
                return value;
            }
        }
        return null;
    }

    private static String text(JsonNode value) {
        return value == null || value.isNull() ? null : value.asText();
    }

    private static Integer integer(JsonNode value) {
        if (value == null || value.isNull() || !value.canConvertToInt()) {
            return null;
        }
        return value.intValue();
    }

    private static Instant toInstant(JsonNode value) {
        if (value == null || value.isNull()) {
            return null;
        }
        if (value.isNumber()) {
            return Instant.ofEpochMilli(value.longValue());
        }
        try {
            return Instant.parse(value.asText());
        } catch (java.time.format.DateTimeParseException ex) {
            log.warn("Skipping Kibana timestamp that is not ISO-8601: {}", value.asText());
            return null;
        }
    }
}
