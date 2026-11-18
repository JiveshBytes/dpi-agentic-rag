package tf.dpi.ingestion;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.concurrent.TimeUnit;

/**
 * Scheduled job that triggers log ingestion from Elasticsearch into the RAG pipeline.
 */
@Component
@ConditionalOnProperty(prefix = "mosip.ingestion.jobs", name = "enabled", havingValue = "true", matchIfMissing = true)
public class LogIngestionJob {

    private static final Logger log = LoggerFactory.getLogger(LogIngestionJob.class);

    private final LogIngestionService logIngestionService;
    private final CheckpointService checkpointService;

    @Autowired
    public LogIngestionJob(LogIngestionService logIngestionService,
                           CheckpointService checkpointService) {
        this.logIngestionService = logIngestionService;
        this.checkpointService = checkpointService;
    }

    /**
     * Runs ingestion on a fixed schedule defined by {@code mosip.ingestion.checkpoint-interval-seconds}.
     */
    @Scheduled(
            fixedDelayString = "${mosip.ingestion.checkpoint-interval-seconds:60}",
            timeUnit = TimeUnit.SECONDS)
    public void ingest() {
        log.info("Starting scheduled log ingestion job");
        try {
            logIngestionService.ingestNewLogs();
            checkpointService.persistCheckpoints();
            log.info("Scheduled log ingestion job completed");
        } catch (Exception ex) {
            Throwable rootCause = ex;
            while (rootCause.getCause() != null && rootCause.getCause() != rootCause) {
                rootCause = rootCause.getCause();
            }
            log.error("Scheduled log ingestion job failed: {}: {}",
                    rootCause.getClass().getSimpleName(), rootCause.getMessage(), ex);
        }
    }
}
