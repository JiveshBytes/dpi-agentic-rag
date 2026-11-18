package tf.dpi.model;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * Persisted ingestion checkpoint tracking per-source processing progress.
 */
@Entity
@Table(name = "ingestion_checkpoint")
public class IngestionCheckpoint {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false, length = 50)
    private String stage;

    @Column(name = "source_index", nullable = false, length = 255)
    private String sourceIndex;

    @Column(name = "last_processed_timestamp")
    private Instant lastProcessedTimestamp;

    @Column(name = "last_processed_event_id")
    private String lastProcessedEventId;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "pagination_cursor", columnDefinition = "jsonb")
    private Map<String, Object> paginationCursor;

    private String status;

    @Column(name = "updated_at")
    private Instant updatedAt;

    public IngestionCheckpoint() {}

    public IngestionCheckpoint(String stage, String sourceIndex, Instant lastProcessedTimestamp) {
        this.stage = stage;
        this.sourceIndex = sourceIndex;
        this.lastProcessedTimestamp = lastProcessedTimestamp;
    }

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }
    public String getStage() { return stage; }
    public void setStage(String stage) { this.stage = stage; }
    public String getSourceIndex() { return sourceIndex; }
    public void setSourceIndex(String sourceIndex) { this.sourceIndex = sourceIndex; }
    public Instant getLastProcessedTimestamp() { return lastProcessedTimestamp; }
    public void setLastProcessedTimestamp(Instant lastProcessedTimestamp) { this.lastProcessedTimestamp = lastProcessedTimestamp; }
    public String getLastProcessedEventId() { return lastProcessedEventId; }
    public void setLastProcessedEventId(String lastProcessedEventId) { this.lastProcessedEventId = lastProcessedEventId; }
    public Map<String, Object> getPaginationCursor() { return paginationCursor; }
    public void setPaginationCursor(Map<String, Object> paginationCursor) { this.paginationCursor = paginationCursor; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
}
