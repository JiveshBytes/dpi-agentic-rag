package tf.dpi.ingestion;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

class ElasticsearchLogReaderTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void mapsKibanaSourceFieldsIntoLogEntry() throws Exception {
        KibanaProxyClient kibana = org.mockito.Mockito.mock(KibanaProxyClient.class);
        Instant start = Instant.parse("2026-11-17T00:00:00Z");
        JsonNode source = objectMapper.readTree("""
                {
                  "@timestamp": "2026-11-17T00:00:01Z",
                  "message": "request failed",
                  "log": {"level": "ERROR"},
                  "service": {"name": "gateway"},
                  "trace": {"id": "trace-1"},
                  "span": {"id": "span-1"}
                }
                """);
        when(kibana.fetchLogs(eq("mosip-logs"), eq("@timestamp"), eq(start),
                any(Instant.class), eq(""), eq(200)))
                .thenReturn(List.of(source));
        ElasticsearchLogReader reader =
                new ElasticsearchLogReader(kibana, objectMapper, "mosip-logs", "@timestamp", "", 200);

        var result = reader.readLogsAfter(start);

        assertEquals(1, result.size());
        assertEquals("trace-1", result.get(0).getTraceId());
        assertEquals("span-1", result.get(0).getSpanId());
        assertEquals("gateway", result.get(0).getServiceName());
        assertEquals("ERROR", result.get(0).getLogLevel());
        assertEquals("request failed", result.get(0).getMessage());
        assertEquals(Instant.parse("2026-11-17T00:00:01Z"), result.get(0).getTimestamp());
        verify(kibana).fetchLogs(eq("mosip-logs"), eq("@timestamp"), eq(start),
                any(Instant.class), eq(""), eq(200));
    }

    @Test
    void serializesSourceWhenDocumentHasNoMessageField() throws Exception {
        KibanaProxyClient kibana = org.mockito.Mockito.mock(KibanaProxyClient.class);
        Instant start = Instant.parse("2026-11-17T00:00:00Z");
        JsonNode source = objectMapper.readTree("""
                {"@timestamp":"2026-11-17T00:00:01Z","level":"INFO","appName":"print"}
                """);
        when(kibana.fetchLogs(eq("mosip-logs"), eq("@timestamp"), eq(start),
                any(Instant.class), eq(""), eq(200)))
                .thenReturn(List.of(source));
        ElasticsearchLogReader reader =
                new ElasticsearchLogReader(kibana, objectMapper, "mosip-logs", "@timestamp", "", 200);

        var result = reader.readLogsAfter(start);

        assertEquals(1, result.size());
        assertEquals("print", result.get(0).getServiceName());
        assertEquals(source.toString(), result.get(0).getMessage());
    }
}
