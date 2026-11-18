package tf.dpi.ingestion;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.Test;

class KibanaProxyClientTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void fetchesWithPitAndUsesConfiguredTimeField() throws Exception {
        List<String> paths = new ArrayList<>();
        List<JsonNode> searches = new ArrayList<>();
        AtomicInteger searchCount = new AtomicInteger();
        HttpServer server = server(exchange -> {
            assertEquals("Basic dXNlcjpwYXNz", exchange.getRequestHeaders().getFirst("Authorization"));
            assertEquals("true", exchange.getRequestHeaders().getFirst("kbn-xsrf"));
            String path = parameter(exchange.getRequestURI().getRawQuery(), "path");
            String method = parameter(exchange.getRequestURI().getRawQuery(), "method");
            paths.add(method + " " + path);
            String response;
            if (path.equals("/niraqa1.ida.auth_transaction/_pit?keep_alive=2m")) {
                response = "{\"id\":\"pit-1\"}";
            } else if (path.equals("/_search")) {
                searches.add(objectMapper.readTree(new String(
                        exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8)));
                response = searchCount.getAndIncrement() == 0
                        ? "{\"hits\":{\"hits\":[{\"_source\":{\"id\":\"a\"},\"sort\":[\"2026-06-17T00:00:00Z\",1]}]}}"
                        : "{\"hits\":{\"hits\":[]}}";
            } else if (path.equals("/_pit")) {
                response = "{\"succeeded\":true}";
            } else {
                throw new AssertionError("Unexpected proxy path: " + path);
            }
            respond(exchange, 200, response);
        });

        try {
            KibanaProxyClient client = client(server);
            Instant start = Instant.parse("2026-06-17T00:00:00Z");
            Instant end = Instant.parse("2026-06-18T00:00:00Z");

            List<JsonNode> result = client.fetchLogs(
                    "niraqa1.ida.auth_transaction", "request_dtimes", start, end, "", 1000);

            assertEquals(1, result.size());
            assertEquals(List.of(
                    "POST /niraqa1.ida.auth_transaction/_pit?keep_alive=2m",
                    "POST /_search",
                    "POST /_search",
                    "DELETE /_pit"), paths);
            JsonNode range = searches.get(0).path("query").path("bool").path("filter").get(0)
                    .path("range").path("request_dtimes");
            assertEquals(start.toString(), range.path("gte").asText());
            assertEquals(end.toString(), range.path("lt").asText());
            assertEquals("request_dtimes", searches.get(0).path("sort").get(0).fieldNames().next());
            assertTrue(searches.get(1).has("search_after"));
        } finally {
            server.stop(0);
        }
    }

    @Test
    void fallsBackToScrollWhenPitIsUnavailable() throws Exception {
        List<String> paths = new ArrayList<>();
        HttpServer server = server(exchange -> {
            String path = parameter(exchange.getRequestURI().getRawQuery(), "path");
            String method = parameter(exchange.getRequestURI().getRawQuery(), "method");
            paths.add(method + " " + path);
            if (path.startsWith("/niraqa1.ida.auth_transaction/_pit")) {
                respond(exchange, 400, "{\"error\":\"no handler found for point in time\"}");
            } else if (path.startsWith("/niraqa1.ida.auth_transaction/_search?scroll=2m")) {
                respond(exchange, 200,
                        "{\"_scroll_id\":\"scroll-1\",\"hits\":{\"hits\":[{\"_source\":{\"id\":\"a\"}}]}}");
            } else if (path.equals("/_search/scroll") && method.equals("DELETE")) {
                respond(exchange, 200, "{\"succeeded\":true}");
            } else if (path.equals("/_search/scroll")) {
                respond(exchange, 200, "{\"_scroll_id\":\"scroll-2\",\"hits\":{\"hits\":[]}}");
            } else {
                throw new AssertionError("Unexpected proxy path: " + path);
            }
        });

        try {
            List<JsonNode> result = client(server).fetchLogs(
                    "niraqa1.ida.auth_transaction",
                    "request_dtimes",
                    Instant.parse("2026-06-17T00:00:00Z"),
                    Instant.parse("2026-06-18T00:00:00Z"),
                    null,
                    100);

            assertEquals(1, result.size());
            assertTrue(paths.contains("POST /niraqa1.ida.auth_transaction/_search?scroll=2m"));
            assertTrue(paths.contains("DELETE /_search/scroll"));
        } finally {
            server.stop(0);
        }
    }

    private HttpServer server(com.sun.net.httpserver.HttpHandler handler) throws IOException {
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/api/console/proxy", handler);
        server.start();
        return server;
    }

    private KibanaProxyClient client(HttpServer server) {
        return new KibanaProxyClient(
                objectMapper,
                "http://127.0.0.1:" + server.getAddress().getPort(),
                "",
                "",
                "user",
                "pass");
    }

    private static String parameter(String query, String name) throws IOException {
        for (String part : query.split("&")) {
            String[] pair = part.split("=", 2);
            if (URLDecoder.decode(pair[0], StandardCharsets.UTF_8).equals(name)) {
                return URLDecoder.decode(pair[1], StandardCharsets.UTF_8);
            }
        }
        throw new IOException("Missing proxy parameter " + name);
    }

    private static void respond(
            com.sun.net.httpserver.HttpExchange exchange, int status, String body) throws IOException {
        byte[] response = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json");
        exchange.sendResponseHeaders(status, response.length);
        exchange.getResponseBody().write(response);
        exchange.close();
    }
}
