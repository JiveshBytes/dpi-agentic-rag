package tf.dpi.model;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * Known incident record used for pattern matching during retrieval.
 */
@Entity
@Table(name = "known_incidents")
public class KnownIncident {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "incident_name", nullable = false)
    private String incidentName;

    @Column(columnDefinition = "text")
    private String description;

    @Column(length = 50)
    private String stage;

    @Column(name = "affected_service", length = 255)
    private String affectedService;

    @Column(name = "error_signature", columnDefinition = "text")
    private String errorSignature;

    @Column(name = "root_cause", columnDefinition = "text")
    private String rootCause;

    @Column(columnDefinition = "text")
    private String resolution;

    @Column(name = "created_at")
    private Instant createdAt;

    public KnownIncident() {}

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }
    public String getIncidentName() { return incidentName; }
    public void setIncidentName(String incidentName) { this.incidentName = incidentName; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public String getStage() { return stage; }
    public void setStage(String stage) { this.stage = stage; }
    public String getAffectedService() { return affectedService; }
    public void setAffectedService(String affectedService) { this.affectedService = affectedService; }
    public String getErrorSignature() { return errorSignature; }
    public void setErrorSignature(String errorSignature) { this.errorSignature = errorSignature; }
    public String getRootCause() { return rootCause; }
    public void setRootCause(String rootCause) { this.rootCause = rootCause; }
    public String getResolution() { return resolution; }
    public void setResolution(String resolution) { this.resolution = resolution; }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
}
