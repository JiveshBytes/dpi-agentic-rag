package tf.dpi.embedding;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import tf.dpi.model.LogChunk;
import java.util.List;

/**
 * Service that generates vector embeddings for log chunks and persists them back to the store.
 */
@Service
public class EmbeddingService {

    private static final Logger log = LoggerFactory.getLogger(EmbeddingService.class);

    private final EmbeddingModel embeddingModel;
    private final JdbcTemplate jdbcTemplate;
    private final String embeddingModelName;
    private final int expectedDimensions;
    private final int embeddingBatchSize;
    private final TransactionTemplate transactionTemplate;

    @Autowired
    public EmbeddingService(EmbeddingModel embeddingModel,
                            JdbcTemplate jdbcTemplate,
                            PlatformTransactionManager transactionManager,
                            @Value("${mosip.ollama.embedding-model:nomic-embed-text}") String embeddingModelName,
                            @Value("${mosip.embedding.dimensions:768}") int expectedDimensions,
                            @Value("${mosip.embedding.batch-size:1}") int embeddingBatchSize) {
        if (expectedDimensions < 1 || embeddingBatchSize < 1) {
            throw new IllegalArgumentException("Embedding dimensions and batch size must be positive.");
        }
        this.embeddingModel = embeddingModel;
        this.jdbcTemplate = jdbcTemplate;
        this.embeddingModelName = embeddingModelName;
        this.expectedDimensions = expectedDimensions;
        this.embeddingBatchSize = embeddingBatchSize;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
    }

    public void embedChunk(LogChunk chunk) {
        if (chunk.getContent() == null || chunk.getContent().isBlank()) {
            log.debug("Skipping embedding for empty chunk {}", chunk.getId());
            return;
        }
        embedBatch(List.of(chunk));
    }

    public synchronized List<LogChunk> embedBatch(List<LogChunk> chunks) {
        log.info("Embedding batch of {} chunks", chunks.size());
        if (chunks.isEmpty()) {
            return chunks;
        }

        for (int start = 0; start < chunks.size(); start += embeddingBatchSize) {
            int end = Math.min(start + embeddingBatchSize, chunks.size());
            List<LogChunk> batch = chunks.subList(start, end);
            generateEmbeddings(batch);
            persistEmbeddings(batch);
        }
        return chunks;
    }

    public void generateEmbeddings(List<LogChunk> chunks) {
        for (int start = 0; start < chunks.size(); start += embeddingBatchSize) {
            int end = Math.min(start + embeddingBatchSize, chunks.size());
            List<LogChunk> batch = chunks.subList(start, end);
            List<float[]> vectors = embeddingModel.embedAll(
                    batch.stream().map(LogChunk::getContent).toList());
            if (vectors.size() != batch.size()) {
                throw new IllegalStateException("Embedding model returned " + vectors.size()
                        + " vectors for " + batch.size() + " chunks.");
            }
            for (int i = 0; i < vectors.size(); i++) {
                float[] vector = vectors.get(i);
                if (vector == null || vector.length != expectedDimensions) {
                    throw new IllegalStateException("Embedding for chunk " + batch.get(i).getId()
                            + " has " + (vector == null ? "null" : vector.length)
                            + " dimensions; PostgreSQL vector column requires " + expectedDimensions + ".");
                }
                batch.get(i).setEmbedding(vector);
                batch.get(i).setEmbeddingModel(embeddingModelName);
            }
        }
    }

    public void persistEmbeddings(List<LogChunk> chunks) {
        if (chunks.isEmpty()) {
            return;
        }
        transactionTemplate.executeWithoutResult(status -> jdbcTemplate.batchUpdate(
                    "UPDATE log_chunks SET embedding = CAST(? AS vector), embedding_model = ? WHERE id = ?",
                    chunks,
                    embeddingBatchSize,
                    (statement, chunk) -> {
                        if (chunk.getId() == null || chunk.getEmbedding() == null) {
                            throw new IllegalStateException(
                                    "Cannot persist an embedding before its chunk has been saved.");
                        }
                        statement.setString(1, formatVector(chunk.getEmbedding()));
                        statement.setString(2, embeddingModelName);
                        statement.setObject(3, chunk.getId());
                    }));
        log.info("Persisted embeddings for {} log chunks", chunks.size());
    }

    private static String formatVector(float[] vector) {
        StringBuilder value = new StringBuilder("[");
        for (int i = 0; i < vector.length; i++) {
            if (i > 0) {
                value.append(',');
            }
            value.append(vector[i]);
        }
        return value.append(']').toString();
    }
}
