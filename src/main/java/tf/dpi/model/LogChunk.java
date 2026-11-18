package tf.dpi.model;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Transient;

/**
 * Representation of a chunked log unit ready for embedding and vector storage.
 */
@Entity
@Table(name = "log_chunks")
public class LogChunk {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false, length = 50)
    private String stage;

    @Column(name = "service_name", length = 150)
    private String serviceName;

    @Column(name = "source_index", length = 255)
    private String sourceIndex;

    @Column(name = "log_level", length = 20)
    private String logLevel;

    @Column(name = "error_type", length = 255)
    private String errorType;

    @Column(name = "error_message", columnDefinition = "text")
    private String errorMessage;

    @Column(name = "trace_id", length = 255)
    private String traceId;

    @Column(name = "chunk_type", length = 50)
    private String chunkType;

    @Column(name = "timestamp_start")
    private Instant timestampStart;

    @Column(name = "timestamp_end")
    private Instant timestampEnd;

    @Column(name = "chunk_text", nullable = false, columnDefinition = "text")
    private String chunkText;

    @Transient
    private float[] embedding;

    @Column(name = "embedding_model", length = 100)
    private String embeddingModel;

    @Column(name = "chunking_version", length = 30)
    private String chunkingVersion;

    public LogChunk() {}

    public LogChunk(String traceId, String serviceName, Instant timestamp, String content) {
        this.traceId = traceId;
        this.serviceName = serviceName;
        this.timestampStart = timestamp;
        this.timestampEnd = timestamp;
        this.chunkText = content;
    }

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }
    public String getStage() { return stage; }
    public void setStage(String stage) { this.stage = stage; }
    public String getTraceId() { return traceId; }
    public void setTraceId(String traceId) { this.traceId = traceId; }
    public String getServiceName() { return serviceName; }
    public void setServiceName(String serviceName) { this.serviceName = serviceName; }
    public String getSourceIndex() { return sourceIndex; }
    public void setSourceIndex(String sourceIndex) { this.sourceIndex = sourceIndex; }
    public String getLogLevel() { return logLevel; }
    public void setLogLevel(String logLevel) { this.logLevel = logLevel; }
    public String getErrorType() { return errorType; }
    public void setErrorType(String errorType) { this.errorType = errorType; }
    public String getErrorMessage() { return errorMessage; }
    public void setErrorMessage(String errorMessage) { this.errorMessage = errorMessage; }
    public String getChunkType() { return chunkType; }
    public void setChunkType(String chunkType) { this.chunkType = chunkType; }
    public Instant getTimestampStart() { return timestampStart; }
    public void setTimestampStart(Instant timestampStart) { this.timestampStart = timestampStart; }
    public Instant getTimestampEnd() { return timestampEnd; }
    public void setTimestampEnd(Instant timestampEnd) { this.timestampEnd = timestampEnd; }
    public Instant getTimestamp() { return timestampStart; }
    public void setTimestamp(Instant timestamp) {
        this.timestampStart = timestamp;
        this.timestampEnd = timestamp;
    }
    public String getContent() { return chunkText; }
    public void setContent(String content) { this.chunkText = content; }
    public String getChunkText() { return chunkText; }
    public void setChunkText(String chunkText) { this.chunkText = chunkText; }
    public float[] getEmbedding() { return embedding; }
    public void setEmbedding(float[] embedding) { this.embedding = embedding; }
    public String getEmbeddingModel() { return embeddingModel; }
    public void setEmbeddingModel(String embeddingModel) { this.embeddingModel = embeddingModel; }
    public String getChunkingVersion() { return chunkingVersion; }
    public void setChunkingVersion(String chunkingVersion) { this.chunkingVersion = chunkingVersion; }
}
