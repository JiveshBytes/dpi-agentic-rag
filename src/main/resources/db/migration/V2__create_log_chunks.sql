-- Create the log_chunks table that stores chunked, masked log entries with embeddings
CREATE TABLE log_chunks (
    id BIGSERIAL PRIMARY KEY,
    trace_id VARCHAR(255) NOT NULL,
    span_id VARCHAR(255),
    service_name VARCHAR(255) NOT NULL,
    log_level VARCHAR(50),
    timestamp TIMESTAMP WITH TIME ZONE NOT NULL,
    message TEXT NOT NULL,
    source_file VARCHAR(512),
    line_number INTEGER,
    embedding vector(768),
    metadata JSONB,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT NOW()
);

CREATE INDEX idx_log_chunks_trace_id ON log_chunks(trace_id);
CREATE INDEX idx_log_chunks_timestamp ON log_chunks(timestamp);
CREATE INDEX idx_log_chunks_service ON log_chunks(service_name);
CREATE INDEX idx_log_chunks_embedding ON log_chunks USING ivfflat (embedding vector_cosine_ops);
