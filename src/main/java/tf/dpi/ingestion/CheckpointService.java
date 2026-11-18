package tf.dpi.ingestion;

import java.time.Instant;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import tf.dpi.model.IngestionCheckpoint;
import tf.dpi.storage.CheckpointRepository;

/**
 * Service that tracks ingestion progress per source so logs are not processed twice.
 */
@Service
public class CheckpointService {

    private static final Logger log = LoggerFactory.getLogger(CheckpointService.class);

    private final CheckpointRepository checkpointRepository;
    private final String stage;
    private final String sourceIndex;
    private Instant pendingTimestamp;

    @Autowired
    public CheckpointService(CheckpointRepository checkpointRepository,
                             @Value("${mosip.stage}") String stage,
                             @Value("${mosip.elasticsearch.index:niraqa1.ida.auth_transaction}") String sourceIndex) {
        this.checkpointRepository = checkpointRepository;
        this.stage = stage;
        this.sourceIndex = sourceIndex;
    }

    public Instant getLastTimestamp() {
        if (pendingTimestamp != null) {
            return pendingTimestamp;
        }
        return checkpointRepository.findByStageAndSourceIndex(stage, sourceIndex)
                .map(IngestionCheckpoint::getLastProcessedTimestamp)
                .filter(timestamp -> timestamp != null)
                .orElse(Instant.EPOCH);
    }

    @Transactional
    public void update(Instant timestamp) {
        pendingTimestamp = timestamp;
        log.debug("Updated checkpoint to {}", timestamp);
    }

    @Transactional
    public void persistCheckpoints() {
        if (pendingTimestamp == null) {
            return;
        }

        IngestionCheckpoint checkpoint = checkpointRepository
                .findByStageAndSourceIndex(stage, sourceIndex)
                .orElseGet(() -> new IngestionCheckpoint(stage, sourceIndex, pendingTimestamp));
        checkpoint.setLastProcessedTimestamp(pendingTimestamp);
        checkpoint.setStatus("COMPLETED");
        checkpoint.setUpdatedAt(Instant.now());
        checkpointRepository.save(checkpoint);
        log.debug("Persisted checkpoint for stage {} and source {}", stage, sourceIndex);
        pendingTimestamp = null;
    }
}
