package tf.dpi.embedding;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.Test;

class OllamaEmbeddingModelTest {

    @Test
    void embedsMultipleInputsInOneOllamaRequest() throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/api/embed", exchange -> {
            assertEquals("POST", exchange.getRequestMethod());
            JsonNode request = mapper.readTree(exchange.getRequestBody());
            assertEquals("qwen3-embedding:0.6b", request.path("model").asText());
            assertEquals(768, request.path("dimensions").asInt());
            assertEquals(2, request.path("input").size());
            assertEquals("first log", request.path("input").get(0).asText());
            assertEquals("second log", request.path("input").get(1).asText());

            var response = mapper.createObjectNode();
            var vectors = response.putArray("embeddings");
            vectors.addArray().add(0.1).add(0.2);
            vectors.addArray().add(0.3).add(0.4);
            byte[] body = mapper.writeValueAsBytes(response);
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
        });
        server.start();

        try {
            OllamaEmbeddingModel model = new OllamaEmbeddingModel(
                    mapper,
                    "http://127.0.0.1:" + server.getAddress().getPort(),
                    "qwen3-embedding:0.6b",
                    768,
                    5);

            List<float[]> vectors = model.embedAll(List.of("first log", "second log"));

            assertEquals(2, vectors.size());
            assertEquals(2, vectors.get(0).length);
            assertEquals(0.1f, vectors.get(0)[0], 0.0001f);
            assertEquals(0.4f, vectors.get(1)[1], 0.0001f);
        } finally {
            server.stop(0);
        }
    }

    @Test
    void rejectsBlankInputBeforeSendingRequest() {
        OllamaEmbeddingModel model = new OllamaEmbeddingModel(
                new ObjectMapper(), "http://localhost:11434", "qwen3-embedding:0.6b", 768, 5);

        org.junit.jupiter.api.Assertions.assertThrows(
                IllegalArgumentException.class, () -> model.embedAll(List.of("ok", " ")));
    }

    @Test
    void retriesTemporaryOllamaServerErrors() throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        AtomicInteger requests = new AtomicInteger();
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/api/embed", exchange -> {
            if (requests.incrementAndGet() == 1) {
                byte[] unavailable = "{\"error\":\"temporarily unavailable\"}"
                        .getBytes(StandardCharsets.UTF_8);
                exchange.sendResponseHeaders(503, unavailable.length);
                exchange.getResponseBody().write(unavailable);
            } else {
                byte[] success = "{\"embeddings\":[[0.1,0.2]]}"
                        .getBytes(StandardCharsets.UTF_8);
                exchange.sendResponseHeaders(200, success.length);
                exchange.getResponseBody().write(success);
            }
            exchange.close();
        });
        server.start();

        try {
            OllamaEmbeddingModel model = new OllamaEmbeddingModel(
                    mapper,
                    "http://127.0.0.1:" + server.getAddress().getPort(),
                    "qwen3-embedding:0.6b",
                    768,
                    5,
                    1);

            List<float[]> vectors = model.embedAll(List.of("retry this log"));

            assertEquals(2, requests.get());
            assertEquals(2, vectors.get(0).length);
        } finally {
            server.stop(0);
        }
    }
}
