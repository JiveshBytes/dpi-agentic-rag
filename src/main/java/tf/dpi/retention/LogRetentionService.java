package tf.dpi.retention;

import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.concurrent.TimeUnit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

/**
 * Deletes expired log chunks using their database creation time.
 */
@Service
public class LogRetentionService {

    private static final Logger log = LoggerFactory.getLogger(LogRetentionService.class);
    private static final String CREATED_BEFORE_CUTOFF = "created_at < ?";

    private final JdbcTemplate jdbcTemplate;
    private final Duration retention;
    private final int batchSize;
    private final int maxBatchesPerRun;
    private final long deleteDelayMillis;
    private final boolean dryRun;
    private final boolean deleteOnlyCompletedRecords;

    public LogRetentionService(
            JdbcTemplate jdbcTemplate,
            @Value("${mosip.retention.retention.days:0}") int retentionDays,
            @Value("${mosip.retention.retention.hours:10}") int retentionHours,
            @Value("${mosip.retention.batch-size:500}") int batchSize,
            @Value("${mosip.retention.max-batches-per-run:10}") int maxBatchesPerRun,
            @Value("${mosip.retention.delete-delay-ms:0}") long deleteDelayMillis,
            @Value("${mosip.retention.dry-run:false}") boolean dryRun,
            @Value("${mosip.retention.delete-only-completed-records:false}") boolean deleteOnlyCompletedRecords) {
        if (retentionDays < 0 || retentionHours < 0 || (retentionDays == 0 && retentionHours == 0)) {
            throw new IllegalArgumentException("Retention duration must be positive and cannot be negative.");
        }
        if (batchSize < 1 || maxBatchesPerRun < 1 || deleteDelayMillis < 0) {
            throw new IllegalArgumentException(
                    "Retention batch size and maximum batches must be positive; delete delay cannot be negative.");
        }

        this.jdbcTemplate = jdbcTemplate;
        this.retention = Duration.ofDays(retentionDays).plusHours(retentionHours);
        this.batchSize = batchSize;
        this.maxBatchesPerRun = maxBatchesPerRun;
        this.deleteDelayMillis = deleteDelayMillis;
        this.dryRun = dryRun;
        this.deleteOnlyCompletedRecords = deleteOnlyCompletedRecords;
    }

    public void applyRetentionPolicy() {
        Instant cutoff = Instant.now().minus(retention);
        OffsetDateTime cutoffParameter = OffsetDateTime.ofInstant(cutoff, ZoneOffset.UTC);
        long eligibleRecords = countEligibleRecords(cutoffParameter);
        long maxRecordsThisRun = (long) batchSize * maxBatchesPerRun;

        if (dryRun) {
            log.info("Retention dry-run: {} eligible log_chunks records before cutoff {}; "
                    + "deletion is disabled (per-run limit: {})",
                    eligibleRecords, cutoff, maxRecordsThisRun);
            return;
        }

        log.info("Purging log_chunks records created before {} (retention {}, completed-only: {})",
                cutoff, retention, deleteOnlyCompletedRecords);
        int deletedRecords = 0;
        int batches = 0;
        while (batches < maxBatchesPerRun) {
            int deletedInBatch = deleteBatch(cutoffParameter);
            if (deletedInBatch == 0) {
                break;
            }
            deletedRecords += deletedInBatch;
            batches++;
            log.info("Retention batch {}/{} deleted {} records ({} total)",
                    batches, maxBatchesPerRun, deletedInBatch, deletedRecords);

            if (deletedInBatch < batchSize || batches >= maxBatchesPerRun) {
                break;
            }
            pauseBeforeNextBatch();
        }

        log.info("Retention purge completed: deleted {} of {} eligible log_chunks records "
                + "in {} batch(es); cutoff={}",
                deletedRecords, eligibleRecords, batches, cutoff);
    }

    private long countEligibleRecords(OffsetDateTime cutoff) {
        String sql = "SELECT COUNT(*) FROM log_chunks WHERE " + eligibleWhereClause();
        Long count = jdbcTemplate.queryForObject(sql, Long.class, cutoff);
        if (count == null) {
            throw new IllegalStateException("Could not count eligible log_chunks records.");
        }
        return count;
    }

    private int deleteBatch(OffsetDateTime cutoff) {
        String sql = """
                WITH candidates AS (
                    SELECT id
                    FROM log_chunks
                    WHERE %s
                    ORDER BY created_at, id
                    LIMIT ?
                )
                DELETE FROM log_chunks AS chunks
                USING candidates
                WHERE chunks.id = candidates.id
                """.formatted(eligibleWhereClause());
        return jdbcTemplate.update(sql, cutoff, batchSize);
    }

    private String eligibleWhereClause() {
        return deleteOnlyCompletedRecords
                ? CREATED_BEFORE_CUTOFF + " AND embedding IS NOT NULL"
                : CREATED_BEFORE_CUTOFF;
    }

    private void pauseBeforeNextBatch() {
        if (deleteDelayMillis == 0) {
            return;
        }
        try {
            TimeUnit.MILLISECONDS.sleep(deleteDelayMillis);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while throttling log retention batches.", ex);
        }
    }
}
