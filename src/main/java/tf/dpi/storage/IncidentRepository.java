package tf.dpi.storage;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import tf.dpi.model.KnownIncident;

import java.util.List;
import java.util.UUID;

/**
 * Repository for known incidents used during retrieval to match against new log patterns.
 */
@Repository
public interface IncidentRepository extends JpaRepository<KnownIncident, UUID> {

    List<KnownIncident> findByStage(String stage);

    boolean existsByIncidentName(String incidentName);

    List<KnownIncident> findByAffectedService(String affectedService);
}
