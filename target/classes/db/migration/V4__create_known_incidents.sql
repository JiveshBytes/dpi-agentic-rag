-- Create the known_incidents table used for incident matching during retrieval
CREATE TABLE known_incidents (
    id BIGSERIAL PRIMARY KEY,
    incident_id VARCHAR(255) NOT NULL UNIQUE,
    title VARCHAR(512) NOT NULL,
    description TEXT,
    severity VARCHAR(50),
    status VARCHAR(50) DEFAULT 'OPEN',
    detected_at TIMESTAMP WITH TIME ZONE DEFAULT NOW(),
    resolved_at TIMESTAMP WITH TIME ZONE,
    related_trace_ids JSONB,
    tags JSONB
);

CREATE INDEX idx_known_incidents_status ON known_incidents(status);
CREATE INDEX idx_known_incidents_severity ON known_incidents(severity);
CREATE INDEX idx_known_incidents_incident_id ON known_incidents(incident_id);
