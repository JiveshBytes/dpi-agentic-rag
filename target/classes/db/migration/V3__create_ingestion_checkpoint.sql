-- Create the ingestion_checkpoint table that tracks per-source ingestion progress
CREATE TABLE ingestion_checkpoint (
    id BIGSERIAL PRIMARY KEY,
    source VARCHAR(255) NOT NULL,
    last_processed_timestamp TIMESTAMP WITH TIME ZONE,
    last_processed_id VARCHAR(255),
    records_processed BIGINT DEFAULT 0,
    status VARCHAR(50) NOT NULL DEFAULT 'ACTIVE',
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT NOW(),
    CONSTRAINT uk_ingestion_checkpoint_source UNIQUE (source)
);

CREATE INDEX idx_ingestion_checkpoint_source ON ingestion_checkpoint(source);
