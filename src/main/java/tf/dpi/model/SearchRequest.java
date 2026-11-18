package tf.dpi.model;

import java.time.Instant;

/**
 * Request object for log retrieval operations.
 */
public class SearchRequest {

    private String queryText;
    private float[] queryVector;
    private String serviceName;
    private String traceId;
    private String logLevel;
    private Instant startTime;
    private Instant endTime;
    private int topK = 10;

    public SearchRequest() {}

    public String getQueryText() { return queryText; }
    public void setQueryText(String queryText) { this.queryText = queryText; }
    public float[] getQueryVector() { return queryVector; }
    public void setQueryVector(float[] queryVector) { this.queryVector = queryVector; }
    public String getServiceName() { return serviceName; }
    public void setServiceName(String serviceName) { this.serviceName = serviceName; }
    public String getTraceId() { return traceId; }
    public void setTraceId(String traceId) { this.traceId = traceId; }
    public String getLogLevel() { return logLevel; }
    public void setLogLevel(String logLevel) { this.logLevel = logLevel; }
    public Instant getStartTime() { return startTime; }
    public void setStartTime(Instant startTime) { this.startTime = startTime; }
    public Instant getEndTime() { return endTime; }
    public void setEndTime(Instant endTime) { this.endTime = endTime; }
    public int getTopK() { return topK; }
    public void setTopK(int topK) { this.topK = topK; }
}
