package tf.dpi.model;

import java.time.Instant;

/**
 * Result object returned from a log retrieval operation.
 */
public class SearchResult {

    private Long chunkId;
    private String traceId;
    private String serviceName;
    private Instant timestamp;
    private String message;
    private Double similarityScore;

    public SearchResult() {}

    public SearchResult(Long chunkId, String traceId, String serviceName,
                        Instant timestamp, String message, Double similarityScore) {
        this.chunkId = chunkId;
        this.traceId = traceId;
        this.serviceName = serviceName;
        this.timestamp = timestamp;
        this.message = message;
        this.similarityScore = similarityScore;
    }

    public Long getChunkId() { return chunkId; }
    public void setChunkId(Long chunkId) { this.chunkId = chunkId; }
    public String getTraceId() { return traceId; }
    public void setTraceId(String traceId) { this.traceId = traceId; }
    public String getServiceName() { return serviceName; }
    public void setServiceName(String serviceName) { this.serviceName = serviceName; }
    public Instant getTimestamp() { return timestamp; }
    public void setTimestamp(Instant timestamp) { this.timestamp = timestamp; }
    public String getMessage() { return message; }
    public void setMessage(String message) { this.message = message; }
    public Double getSimilarityScore() { return similarityScore; }
    public void setSimilarityScore(Double similarityScore) { this.similarityScore = similarityScore; }
}
