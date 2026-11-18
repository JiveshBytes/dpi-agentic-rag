package tf.dpi.retention;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Scheduled job that enforces log retention by purging chunks older than the configured retention period.
 */
@Component
@ConditionalOnProperty(prefix = "mosip.retention", name = "enabled", havingValue = "true")
public class LogRetentionJob {

    private static final Logger log = LoggerFactory.getLogger(LogRetentionJob.class);

    private final LogRetentionService retentionService;

    @Autowired
    public LogRetentionJob(LogRetentionService retentionService) {
        this.retentionService = retentionService;
    }

    @Scheduled(
            cron = "${mosip.retention.cron:0 0 2 * * ?}",
            zone = "${mosip.retention.timezone:UTC}")
    public void run() {
        log.info("Starting log retention job");
        retentionService.applyRetentionPolicy();
        log.info("Log retention job completed");
    }
}
