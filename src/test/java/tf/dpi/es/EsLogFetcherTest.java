package tf.dpi.es;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicInteger;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class EsLogFetcherTest {

    @TempDir
    Path tempDir;

    @Test
    void parsesOffsetAndLocalTimesAsUtc() {
        EsLogFetcher fetcher = fetcher("http://localhost:9200");

        assertEquals("2026-09-29T00:00:00.000Z",
                fetcher.parseDt("2026-09-29T00:00:00Z", "Asia/Kolkata"));
        assertEquals("2026-09-28T18:30:00.000Z",
                fetcher.parseDt("2026-09-29 00:00", "Asia/Kolkata"));
    }

    @Test
    void fetchesPagesClosesPitAndSplitsJsonLines() throws Exception {
        AtomicInteger searches = new AtomicInteger();
        AtomicInteger closedPits = new AtomicInteger();
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/api/console/proxy", exchange -> {
            assertEquals("Basic dXNlcjpwYXNz", exchange.getRequestHeaders().getFirst("Authorization"));
            assertEquals("true", exchange.getRequestHeaders().getFirst("kbn-xsrf"));
            String query = exchange.getRequestURI().getRawQuery();
            String proxyPath = queryParameter(query, "path");
            String response;
            if (proxyPath.equals("/logs-*/_pit?keep_alive=2m")) {
                response = "{\"id\":\"pit-1\"}";
            } else if (proxyPath.equals("/_search")) {
                int page = searches.getAndIncrement();
                response = switch (page) {
                    case 0 -> searchResponse("first");
                    case 1 -> searchResponse("second");
                    default -> "{\"hits\":{\"hits\":[]}}";
                };
            } else if (proxyPath.equals("/_pit")) {
                closedPits.incrementAndGet();
                response = "{\"succeeded\":true}";
            } else {
                throw new AssertionError("Unexpected proxy path: " + proxyPath);
            }
            byte[] body = response.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
        });
        server.start();

        try {
            EsLogFetcher fetcher = new EsLogFetcher(
                    "http://127.0.0.1:" + server.getAddress().getPort(),
                    null, null, "user", "pass");
            Path output = tempDir.resolve("logs.jsonl");
            EsLogFetcher.FetchResult result = fetcher.fetchLogs(new EsLogFetcher.FetchOptions(
                    "logs-*", "2026-09-29T00:00:00.000Z", "2026-09-30T00:00:00.000Z",
                    null, "@timestamp", output, 1, 1, null));

            assertEquals(2, result.count());
            assertEquals(2, result.files().size());
            assertEquals(3, searches.get());
            assertEquals(1, closedPits.get());
            assertEquals("{\"message\":\"first\"}\n",
                    Files.readString(result.files().get(0)));
            assertEquals("{\"message\":\"second\"}\n",
                    Files.readString(result.files().get(1)));
        } finally {
            server.stop(0);
        }
    }

    private static EsLogFetcher fetcher(String url) {
        return new EsLogFetcher(url, null, null, "user", "pass");
    }

    private static String searchResponse(String message) {
        return """
                {"pit_id":"pit-updated","hits":{"hits":[{"_source":{"message":"%s"},"sort":["2026-09-29T00:00:00Z",1]}]}}
                """.formatted(message).trim();
    }

    private static String queryParameter(String query, String name) throws IOException {
        for (String part : query.split("&")) {
            String[] pair = part.split("=", 2);
            if (URLDecoder.decode(pair[0], StandardCharsets.UTF_8).equals(name)) {
                return URLDecoder.decode(pair[1], StandardCharsets.UTF_8);
            }
        }
        throw new IOException("Missing query parameter " + name);
    }
}
