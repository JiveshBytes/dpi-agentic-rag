package tf.dpi.embedding;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class OllamaEmbeddingModel implements EmbeddingModel {

    private static final Logger log = LoggerFactory.getLogger(OllamaEmbeddingModel.class);

    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;
    private final URI embedUri;
    private final String model;
    private final int dimensions;
    private final int maxRetries;
    private final Duration requestTimeout;

    public OllamaEmbeddingModel(
            ObjectMapper objectMapper,
            String host,
            String model,
            int dimensions,
            int timeoutSeconds,
            int maxRetries) {
        if (host == null || host.isBlank()) {
            throw new IllegalArgumentException("Ollama host must not be blank.");
        }
        if (model == null || model.isBlank()) {
            throw new IllegalArgumentException("Ollama embedding model must not be blank.");
        }
        if (dimensions < 1) {
            throw new IllegalArgumentException("Ollama embedding dimensions must be at least one.");
        }
        if (timeoutSeconds < 1) {
            throw new IllegalArgumentException("Ollama request timeout must be at least one second.");
        }
        if (maxRetries < 0) {
            throw new IllegalArgumentException("Ollama retry count cannot be negative.");
        }
        this.objectMapper = objectMapper;
        this.embedUri = URI.create(host.replaceAll("/+$", "") + "/api/embed");
        this.model = model;
        this.dimensions = dimensions;
        this.maxRetries = maxRetries;
        this.requestTimeout = Duration.ofSeconds(timeoutSeconds);
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .build();
    }

    public OllamaEmbeddingModel(
            ObjectMapper objectMapper, String host, String model, int dimensions, int timeoutSeconds) {
        this(objectMapper, host, model, dimensions, timeoutSeconds, 0);
    }

    public OllamaEmbeddingModel(ObjectMapper objectMapper, String host, String model, int timeoutSeconds) {
        this(objectMapper, host, model, 768, timeoutSeconds);
    }

    @Override
    public float[] embed(String text) {
        return embedAll(List.of(text)).get(0);
    }

    @Override
    public List<float[]> embedAll(List<String> texts) {
        if (texts == null || texts.isEmpty()) {
            return List.of();
        }
        if (texts.stream().anyMatch(text -> text == null || text.isBlank())) {
            throw new IllegalArgumentException("Ollama cannot embed blank text.");
        }

        ObjectNode body = objectMapper.createObjectNode();
        body.put("model", model);
        body.put("dimensions", dimensions);
        ArrayNode input = body.putArray("input");
        texts.forEach(input::add);

        HttpRequest request;
        try {
            request = HttpRequest.newBuilder(embedUri)
                    .timeout(requestTimeout)
                    .header("Content-Type", "application/json")
                    .header("Accept", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(
                            objectMapper.writeValueAsString(body), StandardCharsets.UTF_8))
                    .build();
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("Could not serialize Ollama embedding request.", ex);
        }

        for (int attempt = 0; ; attempt++) {
            try {
                HttpResponse<String> response = httpClient.send(request,
                        HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
                if (response.statusCode() == 429 || response.statusCode() >= 500) {
                    if (attempt < maxRetries) {
                        pauseBeforeRetry(attempt);
                        continue;
                    }
                }
                if (response.statusCode() < 200 || response.statusCode() >= 300) {
                    String detail = response.body();
                    if (detail.length() > 500) {
                        detail = detail.substring(0, 500);
                    }
                    throw new IllegalStateException("Ollama embedding request failed with HTTP "
                            + response.statusCode() + ": " + detail);
                }
                JsonNode root = objectMapper.readTree(response.body());
                JsonNode vectors = root.path("embeddings");
                if (!vectors.isArray() || vectors.size() != texts.size()) {
                    throw new IllegalStateException("Ollama returned "
                            + (vectors.isArray() ? vectors.size() : "no")
                            + " embeddings for " + texts.size() + " inputs.");
                }
                List<float[]> result = new ArrayList<>(vectors.size());
                for (JsonNode vectorNode : vectors) {
                    if (!vectorNode.isArray() || vectorNode.isEmpty()) {
                        throw new IllegalStateException("Ollama returned an empty or invalid embedding vector.");
                    }
                    float[] vector = new float[vectorNode.size()];
                    for (int i = 0; i < vector.length; i++) {
                        JsonNode value = vectorNode.get(i);
                        if (!value.isNumber()) {
                            throw new IllegalStateException("Ollama embedding contained a non-numeric value.");
                        }
                        vector[i] = value.floatValue();
                    }
                    result.add(vector);
                }
                int dimension = result.get(0).length;
                if (result.stream().anyMatch(vector -> vector.length != dimension)) {
                    throw new IllegalStateException("Ollama returned vectors with inconsistent dimensions.");
                }
                return List.copyOf(result);
            } catch (HttpTimeoutException ex) {
                if (attempt >= maxRetries) {
                    throw new IllegalStateException("Ollama embedding request timed out after "
                            + (attempt + 1) + " attempt(s) at " + embedUri + ".", ex);
                }
                logRetry(attempt, maxRetries, texts.size(), ex);
                pauseBeforeRetry(attempt);
            } catch (JsonProcessingException ex) {
                throw new IllegalStateException("Ollama returned invalid embedding JSON.", ex);
            } catch (IOException ex) {
                if (attempt >= maxRetries) {
                    throw new IllegalStateException("Could not communicate with Ollama at " + embedUri + ".", ex);
                }
                logRetry(attempt, maxRetries, texts.size(), ex);
                pauseBeforeRetry(attempt);
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException("Interrupted while requesting Ollama embeddings.", ex);
            }
        }
    }

    private static void pauseBeforeRetry(int attempt) {
        long backoffMillis = Math.min(1_000L << attempt, 8_000L);
        try {
            Thread.sleep(backoffMillis);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while waiting to retry Ollama embeddings.", ex);
        }
    }

    private static void logRetry(int attempt, int maxRetries, int inputCount, Exception ex) {
        log.warn("Ollama embedding request for {} inputs failed; retry {}/{}: {}",
                inputCount, attempt + 1, maxRetries, ex.getMessage());
    }
}
