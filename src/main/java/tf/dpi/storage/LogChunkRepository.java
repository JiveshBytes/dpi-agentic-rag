package tf.dpi.storage;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import tf.dpi.model.LogChunk;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Repository for persisting and querying log chunks (including pgvector embeddings).
 */
@Repository
public interface LogChunkRepository extends JpaRepository<LogChunk, UUID> {

    List<LogChunk> findByTraceId(String traceId);

    @Query(value = """
            SELECT id, stage, service_name, source_index, log_level, error_type,
                   error_message, trace_id, chunk_type, timestamp_start, timestamp_end,
                   chunk_text, embedding_model, chunking_version
            FROM log_chunks
            WHERE embedding IS NULL
            ORDER BY timestamp_start NULLS FIRST, id
            LIMIT :batchSize
            """, nativeQuery = true)
    List<LogChunk> findNextUnembeddedBatch(@Param("batchSize") int batchSize);

    Optional<LogChunk> findById(UUID id);
}
