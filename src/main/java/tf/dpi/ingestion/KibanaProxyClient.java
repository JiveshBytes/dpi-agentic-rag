package tf.dpi.ingestion;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class KibanaProxyClient {

    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;
    private final URI proxyUri;
    private final String apiKey;
    private final String username;
    private final String password;

    public KibanaProxyClient(
            ObjectMapper objectMapper,
            @Value("${mosip.elasticsearch.kibana-url:}") String kibanaUrl,
            @Value("${mosip.elasticsearch.kibana-space:}") String space,
            @Value("${mosip.elasticsearch.api-key:}") String apiKey,
            @Value("${mosip.elasticsearch.username:}") String username,
            @Value("${mosip.elasticsearch.password:}") String password) {
        this.objectMapper = objectMapper;
        this.apiKey = apiKey;
        this.username = username;
        this.password = password;
        if (kibanaUrl == null || kibanaUrl.isBlank()) {
            this.proxyUri = null;
        } else {
            String base = kibanaUrl.replaceAll("/+$", "");
            if (space != null && !space.isBlank()) {
                base += "/s/" + URLEncoder.encode(space, StandardCharsets.UTF_8).replace("+", "%20");
            }
            this.proxyUri = URI.create(base + "/api/console/proxy");
        }
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(20))
                .build();
    }

    public List<JsonNode> fetchLogs(
            String index,
            String timeField,
            Instant start,
            Instant end,
            String query,
            int pageSize) throws IOException, InterruptedException {
        if (index == null || index.isBlank()) {
            throw new IllegalArgumentException("Elasticsearch index pattern must not be blank.");
        }
        if (timeField == null || timeField.isBlank()) {
            throw new IllegalArgumentException("Elasticsearch time field must not be blank.");
        }
        if (start == null || end == null || !start.isBefore(end)) {
            throw new IllegalArgumentException("Log fetch start must be before its end.");
        }
        if (pageSize < 1) {
            throw new IllegalArgumentException("Elasticsearch page size must be at least one.");
        }

        String pitId;
        try {
            pitId = openPointInTime(index);
        } catch (KibanaHttpException ex) {
            if (isPointInTimeUnsupported(ex)) {
                return fetchLogsWithScroll(index, timeField, start, end, query, pageSize);
            }
            throw ex;
        }
        List<JsonNode> sources = new ArrayList<>();
        Throwable fetchFailure = null;
        try {
            List<JsonNode> searchAfter = null;
            while (true) {
                ObjectNode body = buildSearchBody(timeField, start, end, query, pageSize, pitId, searchAfter);
                JsonNode response = request("POST", "/_search", body);
                pitId = response.path("pit_id").asText(pitId);
                JsonNode hits = response.path("hits").path("hits");
                if (!hits.isArray() || hits.isEmpty()) {
                    break;
                }

                for (JsonNode hit : hits) {
                    JsonNode source = hit.get("_source");
                    if (source != null && source.isObject()) {
                        sources.add(source.deepCopy());
                    }
                }

                JsonNode sortValues = hits.get(hits.size() - 1).get("sort");
                if (sortValues == null || !sortValues.isArray() || sortValues.isEmpty()) {
                    throw new IOException("Kibana search response omitted sort values required for pagination.");
                }
                searchAfter = new ArrayList<>();
                for (JsonNode sortValue : sortValues) {
                    searchAfter.add(sortValue.deepCopy());
                }
            }
            return sources;
        } catch (IOException | InterruptedException | RuntimeException ex) {
            fetchFailure = ex;
            throw ex;
        } finally {
            try {
                closePointInTime(pitId);
            } catch (IOException | InterruptedException closeFailure) {
                if (fetchFailure != null) {
                    fetchFailure.addSuppressed(closeFailure);
                } else if (closeFailure instanceof IOException ioException) {
                    throw ioException;
                } else {
                    Thread.currentThread().interrupt();
                    throw (InterruptedException) closeFailure;
                }
            }
        }
    }

    private String openPointInTime(String index) throws IOException, InterruptedException {
        JsonNode response = request("POST", "/" + index + "/_pit?keep_alive=2m", null);
        String id = response.path("id").asText(null);
        if (id == null || id.isBlank()) {
            throw new IOException("Kibana response did not contain a point-in-time ID.");
        }
        return id;
    }

    private List<JsonNode> fetchLogsWithScroll(
            String index,
            String timeField,
            Instant start,
            Instant end,
            String query,
            int pageSize) throws IOException, InterruptedException {
        ObjectNode body = objectMapper.createObjectNode();
        body.put("size", pageSize);
        ArrayNode sorts = body.putArray("sort");
        sorts.addObject().put(timeField, "asc");
        addFilters(body, timeField, start, end, query);

        JsonNode response = request("POST", "/" + index + "/_search?scroll=2m", body);
        String scrollId = response.path("_scroll_id").asText(null);
        List<JsonNode> sources = new ArrayList<>();
        Throwable fetchFailure = null;
        try {
            while (true) {
                JsonNode hits = response.path("hits").path("hits");
                if (!hits.isArray() || hits.isEmpty()) {
                    break;
                }
                for (JsonNode hit : hits) {
                    JsonNode source = hit.get("_source");
                    if (source != null && source.isObject()) {
                        sources.add(source.deepCopy());
                    }
                }
                if (scrollId == null || scrollId.isBlank()) {
                    throw new IOException("Elasticsearch scroll response omitted the scroll ID.");
                }
                ObjectNode nextPage = objectMapper.createObjectNode();
                nextPage.put("scroll", "2m");
                nextPage.put("scroll_id", scrollId);
                response = request("POST", "/_search/scroll", nextPage);
                scrollId = response.path("_scroll_id").asText(scrollId);
            }
            return sources;
        } catch (IOException | InterruptedException | RuntimeException ex) {
            fetchFailure = ex;
            throw ex;
        } finally {
            if (scrollId != null && !scrollId.isBlank()) {
                try {
                    ObjectNode closeBody = objectMapper.createObjectNode();
                    closeBody.putArray("scroll_id").add(scrollId);
                    request("DELETE", "/_search/scroll", closeBody);
                } catch (IOException | InterruptedException closeFailure) {
                    if (fetchFailure != null) {
                        fetchFailure.addSuppressed(closeFailure);
                    } else if (closeFailure instanceof IOException ioException) {
                        throw ioException;
                    } else {
                        Thread.currentThread().interrupt();
                        throw (InterruptedException) closeFailure;
                    }
                }
            }
        }
    }

    private boolean isPointInTimeUnsupported(KibanaHttpException ex) {
        if (ex.statusCode() != 400 && ex.statusCode() != 404) {
            return false;
        }
        String message = ex.getMessage().toLowerCase(java.util.Locale.ROOT);
        return message.contains("point in time")
                || message.contains("unknown endpoint")
                || message.contains("no handler found");
    }

    private void closePointInTime(String pitId) throws IOException, InterruptedException {
        ObjectNode body = objectMapper.createObjectNode();
        body.put("id", pitId);
        request("DELETE", "/_pit", body);
    }

    private ObjectNode buildSearchBody(String timeField, Instant start, Instant end, String query,
                                       int pageSize, String pitId, List<JsonNode> searchAfter) {
        ObjectNode body = objectMapper.createObjectNode();
        body.put("size", pageSize);
        body.put("track_total_hits", false);

        addFilters(body, timeField, start, end, query);

        ArrayNode sort = body.putArray("sort");
        sort.addObject().put(timeField, "asc");
        sort.addObject().put("_shard_doc", "asc");
        ObjectNode pit = body.putObject("pit");
        pit.put("id", pitId);
        pit.put("keep_alive", "2m");
        if (searchAfter != null) {
            ArrayNode values = body.putArray("search_after");
            searchAfter.forEach(values::add);
        }
        return body;
    }

    private void addFilters(ObjectNode body, String timeField, Instant start, Instant end, String query) {
        ArrayNode filters = body.putObject("query").putObject("bool").putArray("filter");
        ObjectNode filter = filters.addObject().putObject("range").putObject(timeField);
        filter.put("gte", start.toString());
        filter.put("lt", end.toString());
        filter.put("format", "strict_date_optional_time");
        if (query != null && !query.isBlank()) {
            filters.addObject().putObject("query_string").put("query", query);
        }
    }

    private JsonNode request(String method, String path, JsonNode body)
            throws IOException, InterruptedException {
        if (proxyUri == null) {
            throw new IllegalStateException("Kibana URL is not configured. Set KIBANA_URL.");
        }
        String authorization = authorization();
        String encodedPath = URLEncoder.encode(path, StandardCharsets.UTF_8).replace("+", "%20");
        URI uri = URI.create(proxyUri + "?path=" + encodedPath + "&method=" + method);
        HttpRequest.Builder request = HttpRequest.newBuilder(uri)
                .timeout(Duration.ofSeconds(120))
                .header("Authorization", authorization)
                .header("kbn-xsrf", "true")
                .header("Accept", "application/json");
        if (body == null) {
            request.POST(HttpRequest.BodyPublishers.noBody());
        } else {
            request.header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(toJson(body)));
        }

        HttpResponse<String> response = httpClient.send(request.build(),
                HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            String responseBody = response.body();
            if (responseBody.length() > 500) {
                responseBody = responseBody.substring(0, 500);
            }
            throw new KibanaHttpException(response.statusCode(),
                    "Kibana Console proxy returned HTTP " + response.statusCode() + ": " + responseBody);
        }
        try {
            return objectMapper.readTree(response.body());
        } catch (JsonProcessingException ex) {
            throw new IOException("Kibana Console proxy returned invalid JSON.", ex);
        }
    }

    private String authorization() {
        if (apiKey != null && !apiKey.isBlank()) {
            return "ApiKey " + apiKey;
        }
        if (username != null && !username.isBlank() && password != null && !password.isBlank()) {
            String credentials = username + ":" + password;
            return "Basic " + Base64.getEncoder().encodeToString(credentials.getBytes(StandardCharsets.UTF_8));
        }
        throw new IllegalStateException(
                "Set KIBANA_API_KEY or both KIBANA_USER and KIBANA_PASSWORD to fetch logs.");
    }

    private String toJson(JsonNode body) throws IOException {
        return objectMapper.writeValueAsString(body);
    }

    private static final class KibanaHttpException extends IOException {
        private final int statusCode;

        private KibanaHttpException(int statusCode, String message) {
            super(message);
            this.statusCode = statusCode;
        }

        private int statusCode() {
            return statusCode;
        }
    }
}
