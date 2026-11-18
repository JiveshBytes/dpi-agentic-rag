package tf.dpi.storage;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import tf.dpi.model.IngestionCheckpoint;

import java.util.Optional;
import java.util.UUID;

/**
 * Repository for ingestion checkpoints that track per-source processing progress.
 */
@Repository
public interface CheckpointRepository extends JpaRepository<IngestionCheckpoint, UUID> {

    Optional<IngestionCheckpoint> findByStageAndSourceIndex(String stage, String sourceIndex);
}
