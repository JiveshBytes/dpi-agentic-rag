package tf.dpi.es;

import java.io.BufferedWriter;
import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeFormatterBuilder;
import java.time.temporal.ChronoField;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

public class EsLogFetcher {

    private static final String DEFAULT_TIME_FIELD = "@timestamp";
    private static final String DEFAULT_OUTPUT_DIR = "D:\\logs";
    private static final DateTimeFormatter UTC_MILLIS =
            DateTimeFormatter.ofPattern("uuuu-MM-dd'T'HH:mm:ss.SSS'Z'").withZone(ZoneOffset.UTC);
    private static final DateTimeFormatter LOCAL_DATE_TIME = new DateTimeFormatterBuilder()
            .appendPattern("uuuu-MM-dd['T'][' ']HH:mm")
            .optionalStart()
            .appendPattern(":ss")
            .optionalStart()
            .appendFraction(ChronoField.NANO_OF_SECOND, 0, 9, true)
            .optionalEnd()
            .optionalEnd()
            .toFormatter();

    private final HttpClient httpClient;
    private final ObjectMapper mapper;
    private final URI proxyUri;
    private final String authorization;

    public EsLogFetcher(String url, String space, String apiKey, String username, String password) {
        String base = Objects.requireNonNull(url, "Kibana URL is required").replaceAll("/+$", "");
        if (space != null && !space.isBlank()) {
            base += "/s/" + encodePathSegment(space);
        }
        this.proxyUri = URI.create(base + "/api/console/proxy");
        this.httpClient = HttpClient.newBuilder().connectTimeout(java.time.Duration.ofSeconds(20)).build();
        this.mapper = new ObjectMapper();

        if (apiKey != null && !apiKey.isBlank()) {
            this.authorization = "ApiKey " + apiKey;
        } else if (username != null && !username.isBlank() && password != null) {
            String credentials = username + ":" + password;
            this.authorization = "Basic " + Base64.getEncoder()
                    .encodeToString(credentials.getBytes(StandardCharsets.UTF_8));
        } else {
            throw new IllegalArgumentException(
                    "Set KIBANA_API_KEY or both KIBANA_USER and KIBANA_PASSWORD.");
        }
    }

    public String parseDt(String value, String tz) {
        Objects.requireNonNull(value, "Datetime is required");
        ZoneId zone = ZoneId.of(tz);
        try {
            return UTC_MILLIS.format(Instant.parse(value));
        } catch (java.time.format.DateTimeParseException ignored) {
            // Continue with offset-aware and local datetime formats.
        }
        try {
            return UTC_MILLIS.format(OffsetDateTime.parse(value).toInstant());
        } catch (java.time.format.DateTimeParseException ignored) {
            // A timezone-naive value is interpreted in the supplied zone.
        }
        try {
            return UTC_MILLIS.format(LocalDateTime.parse(value, LOCAL_DATE_TIME).atZone(zone).toInstant());
        } catch (java.time.format.DateTimeParseException ignored) {
            return UTC_MILLIS.format(LocalDate.parse(value).atStartOfDay(zone).toInstant());
        }
    }

    public FetchResult fetchLogs(FetchOptions options) throws IOException, InterruptedException {
        Instant start = Instant.parse(options.start());
        Instant end = Instant.parse(options.end());
        if (!start.isBefore(end)) {
            throw new IllegalArgumentException("Start datetime must be before end datetime.");
        }
        if (options.pageSize() < 1) {
            throw new IllegalArgumentException("Page size must be at least 1.");
        }
        if (options.limit() != null && options.limit() < 1) {
            throw new IllegalArgumentException("Limit must be at least 1.");
        }
        if (options.maxBytes() < 0) {
            throw new IllegalArgumentException("Maximum output size cannot be negative.");
        }

        String pitId = openPointInTime(options.index());
        long count = 0;
        IOException fetchFailure = null;
        try (SplitWriter writer = new SplitWriter(options.output(), options.maxBytes())) {
            List<JsonNode> searchAfter = null;
            while (options.limit() == null || count < options.limit()) {
                ObjectNode body = buildSearchBody(options, pitId, searchAfter);
                JsonNode response = request("POST", "/_search", body);
                pitId = response.path("pit_id").asText(pitId);
                JsonNode hits = response.path("hits").path("hits");
                if (!hits.isArray() || hits.isEmpty()) {
                    break;
                }

                for (JsonNode hit : hits) {
                    JsonNode source = hit.get("_source");
                    if (source != null) {
                        writer.write(mapper.writeValueAsString(source) + "\n");
                        count++;
                        if (options.limit() != null && count >= options.limit()) {
                            break;
                        }
                    }
                }

                JsonNode lastSort = hits.get(hits.size() - 1).get("sort");
                if (lastSort == null || lastSort.isEmpty()) {
                    throw new IOException("Kibana search response omitted the sort values needed for pagination.");
                }
                searchAfter = new ArrayList<>();
                lastSort.forEach(searchAfter::add);
            }
            return new FetchResult(count, writer.files());
        } catch (IOException ex) {
            fetchFailure = ex;
            throw ex;
        } finally {
            try {
                closePointInTime(pitId);
            } catch (IOException closeFailure) {
                if (fetchFailure != null) {
                    fetchFailure.addSuppressed(closeFailure);
                } else {
                    throw closeFailure;
                }
            }
        }
    }

    public List<IndexInfo> listIndices(String pattern) throws IOException, InterruptedException {
        String path = "/_cat/indices/" + encodePathSegment(pattern)
                + "?format=json&h=index,docs.count&s=index";
        JsonNode rows = request("GET", path, null);
        List<IndexInfo> indices = new ArrayList<>();
        for (JsonNode row : rows) {
            String name = row.path("index").asText();
            if (!name.startsWith(".")) {
                indices.add(new IndexInfo(name, row.path("docs.count").asText("?")));
            }
        }
        return indices;
    }

    public ProbeResult probe(String index, String timeField) throws IOException, InterruptedException {
        ObjectNode body = mapper.createObjectNode();
        body.put("size", 0);
        body.put("track_total_hits", true);
        ObjectNode aggs = body.putObject("aggs");
        aggs.putObject("min").putObject("min").put("field", timeField);
        aggs.putObject("max").putObject("max").put("field", timeField);
        JsonNode response = request("POST", "/" + index + "/_search", body);
        long total = response.path("hits").path("total").path("value").asLong();
        String minimum = response.path("aggregations").path("min").path("value_as_string").asText(null);
        String maximum = response.path("aggregations").path("max").path("value_as_string").asText(null);
        return new ProbeResult(total, minimum, maximum);
    }

    private String openPointInTime(String index) throws IOException, InterruptedException {
        JsonNode response = request("POST", "/" + index + "/_pit?keep_alive=2m", null);
        String id = response.path("id").asText(null);
        if (id == null || id.isBlank()) {
            throw new IOException("Kibana response did not contain a point-in-time ID.");
        }
        return id;
    }

    private void closePointInTime(String pitId) throws IOException, InterruptedException {
        ObjectNode body = mapper.createObjectNode();
        body.put("id", pitId);
        request("DELETE", "/_pit", body);
    }

    private ObjectNode buildSearchBody(FetchOptions options, String pitId, List<JsonNode> searchAfter) {
        ObjectNode body = mapper.createObjectNode();
        body.put("size", options.pageSize());
        body.put("track_total_hits", false);

        ObjectNode bool = body.putObject("query").putObject("bool");
        ArrayNode filters = bool.putArray("filter");
        ObjectNode range = filters.addObject().putObject("range").putObject(options.timeField());
        range.put("gte", options.start());
        range.put("lt", options.end());
        range.put("format", "strict_date_optional_time");
        if (options.query() != null && !options.query().isBlank()) {
            filters.addObject().putObject("query_string").put("query", options.query());
        }

        ArrayNode sort = body.putArray("sort");
        sort.addObject().put(options.timeField(), "asc");
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

    private JsonNode request(String method, String path, JsonNode body)
            throws IOException, InterruptedException {
        String encodedPath = URLEncoder.encode(path, StandardCharsets.UTF_8).replace("+", "%20");
        URI uri = URI.create(proxyUri + "?path=" + encodedPath + "&method=" + method);
        HttpRequest.Builder request = HttpRequest.newBuilder(uri)
                .timeout(java.time.Duration.ofSeconds(120))
                .header("Authorization", authorization)
                .header("kbn-xsrf", "true")
                .header("Accept", "application/json");
        if (body != null) {
            request.header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(mapper.writeValueAsString(body)));
        } else {
            request.POST(HttpRequest.BodyPublishers.noBody());
        }

        HttpResponse<String> response = httpClient.send(request.build(),
                HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            String responseBody = response.body();
            if (responseBody.length() > 500) {
                responseBody = responseBody.substring(0, 500);
            }
            throw new IOException("Kibana proxy returned HTTP " + response.statusCode() + ": " + responseBody);
        }
        try {
            return mapper.readTree(response.body());
        } catch (com.fasterxml.jackson.core.JsonProcessingException ex) {
            throw new IOException("Kibana proxy returned an invalid JSON response.", ex);
        }
    }

    private static String encodePathSegment(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8).replace("+", "%20");
    }

    private static Path defaultOutput(String start, String end) {
        String name = "kibana_logs_" + start.replace(':', '-') + "_to_" + end.replace(':', '-') + ".jsonl";
        return Path.of(DEFAULT_OUTPUT_DIR, name);
    }

    public static void main(String[] args) throws Exception {
        Map<String, String> values = new java.util.LinkedHashMap<>();
        boolean listIndices = false;
        if (args.length == 0 || java.util.Arrays.asList(args).contains("--help")) {
            printUsage();
            return;
        }
        for (int i = 0; i < args.length; i++) {
            if ("--list-indices".equals(args[i])) {
                listIndices = true;
                continue;
            }
            if (!args[i].startsWith("--") || i + 1 >= args.length) {
                throw new IllegalArgumentException("Unexpected argument: " + args[i]);
            }
            values.put(args[i].substring(2), args[++i]);
        }

        String url = required(values, "url");
        String space = values.get("space");
        EsLogFetcher fetcher = new EsLogFetcher(url, space,
                System.getenv("KIBANA_API_KEY"),
                System.getenv("KIBANA_USER"),
                System.getenv("KIBANA_PASSWORD"));

        if (listIndices) {
            for (IndexInfo index : fetcher.listIndices(values.getOrDefault("index", "*"))) {
                System.out.printf("%-60s %s docs%n", index.name(), index.documentCount());
            }
            return;
        }

        String index = required(values, "index");
        String timeField = values.getOrDefault("time-field", DEFAULT_TIME_FIELD);
        String zone = values.getOrDefault("tz", "UTC");
        String start = fetcher.parseDt(required(values, "start"), zone);
        String end = fetcher.parseDt(required(values, "end"), zone);
        Path output = values.containsKey("output")
                ? Path.of(values.get("output"))
                : defaultOutput(start, end);
        long maxBytes = (long) (Double.parseDouble(values.getOrDefault("max-size-mb", "400")) * 1024 * 1024);
        Integer limit = values.containsKey("limit") ? Integer.parseInt(values.get("limit")) : null;
        int pageSize = Integer.parseInt(values.getOrDefault("page-size", "1000"));
        FetchOptions options = new FetchOptions(index, start, end, values.get("query"), timeField,
                output, maxBytes, pageSize, limit);

        FetchResult result = fetcher.fetchLogs(options);
        System.out.printf("Fetched %d log entries (%s -> %s)%n", result.count(), start, end);
        for (Path file : result.files()) {
            System.out.printf("Wrote %s (%.1f MB)%n", file,
                    Files.size(file) / (1024.0 * 1024.0));
        }
        if (result.count() == 0) {
            ProbeResult probe = fetcher.probe(index, timeField);
            System.out.printf("%nDiagnosis for index pattern '%s':%n", index);
            if (probe.totalDocuments() == 0) {
                System.out.println("  - It matches no documents. Check the index pattern.");
            } else if (probe.minimumTimestamp() == null) {
                System.out.printf("  - It has %d documents, but none have '%s'.%n",
                        probe.totalDocuments(), timeField);
            } else {
                System.out.printf("  - It has %d documents, from %s to %s (UTC).%n",
                        probe.totalDocuments(), probe.minimumTimestamp(), probe.maximumTimestamp());
                System.out.println("  - Check the requested time range and optional query.");
            }
        }
    }

    private static void printUsage() {
        System.out.println("""
                Usage:
                  java ... ElasticsearchLogFetcherApplication --url <Kibana URL> --index <pattern>
                    --start <datetime> --end <datetime> [--tz UTC] [--query <Lucene query>]
                    [--time-field @timestamp] [--space <space>] [--page-size 1000]
                    [--limit <count>] [--max-size-mb 400] [--output <path>]
                  java ... ElasticsearchLogFetcherApplication --url <Kibana URL>
                    [--space <space>] --list-indices [--index <pattern>]

                Authentication comes from KIBANA_API_KEY, or KIBANA_USER and KIBANA_PASSWORD.
                Datetime values without a timezone are interpreted using --tz.
                """);
    }

    private static String required(Map<String, String> values, String name) {
        String value = values.get(name);
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("Missing required argument --" + name + ".");
        }
        return value;
    }

    public record FetchOptions(String index, String start, String end, String query, String timeField,
                               Path output, long maxBytes, int pageSize, Integer limit) {}

    public record FetchResult(long count, List<Path> files) {}

    public record IndexInfo(String name, String documentCount) {}

    public record ProbeResult(long totalDocuments, String minimumTimestamp, String maximumTimestamp) {}

    private static final class SplitWriter implements AutoCloseable {
        private final Path output;
        private final long maxBytes;
        private final List<Path> files = new ArrayList<>();
        private BufferedWriter writer;
        private long currentSize;

        private SplitWriter(Path output, long maxBytes) {
            this.output = output;
            this.maxBytes = maxBytes;
        }

        private void write(String line) throws IOException {
            byte[] bytes = line.getBytes(StandardCharsets.UTF_8);
            if (writer == null || (maxBytes > 0 && currentSize > 0 && currentSize + bytes.length > maxBytes)) {
                openNext();
            }
            writer.write(line);
            currentSize += bytes.length;
        }

        private void openNext() throws IOException {
            closeWriter();
            Path parent = output.toAbsolutePath().getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
            int part = files.size() + 1;
            String fileName = output.getFileName().toString();
            int extension = fileName.lastIndexOf('.');
            String base = extension > 0 ? fileName.substring(0, extension) : fileName;
            String suffix = extension > 0 ? fileName.substring(extension) : "";
            Path next = output.resolveSibling(base + String.format("_part%03d", part) + suffix);
            writer = Files.newBufferedWriter(next, StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
            files.add(next);
            currentSize = 0;
        }

        private List<Path> files() {
            return List.copyOf(files);
        }

        private void closeWriter() throws IOException {
            if (writer != null) {
                writer.close();
                writer = null;
            }
        }

        @Override
        public void close() throws IOException {
            closeWriter();
        }
    }
}
