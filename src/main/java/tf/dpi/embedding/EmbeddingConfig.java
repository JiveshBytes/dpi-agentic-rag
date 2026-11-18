package tf.dpi.embedding;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.beans.factory.annotation.Value;

import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Configuration for embedding model beans.
 * <p>
 * Configures the Ollama embedding client.
 */
@Configuration
public class EmbeddingConfig {

    @Bean
    public EmbeddingModel embeddingModel(
            ObjectMapper objectMapper,
            @Value("${mosip.ollama.host:http://localhost:11434}") String host,
            @Value("${mosip.ollama.embedding-model:nomic-embed-text}") String model,
            @Value("${mosip.embedding.dimensions:768}") int dimensions,
            @Value("${mosip.ollama.request-timeout-seconds:300}") int timeoutSeconds,
            @Value("${mosip.ollama.max-retries:2}") int maxRetries) {
        return new OllamaEmbeddingModel(objectMapper, host, model, dimensions, timeoutSeconds, maxRetries);
    }
}
