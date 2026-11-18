package tf.dpi.retention;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.time.OffsetDateTime;

import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

class LogRetentionServiceTest {

    @Test
    void dryRunCountsEligibleRecordsWithoutDeleting() {
        JdbcTemplate jdbcTemplate = org.mockito.Mockito.mock(JdbcTemplate.class);
        when(jdbcTemplate.queryForObject(anyString(), eq(Long.class), any(Object[].class)))
                .thenReturn(12L);
        LogRetentionService service = service(jdbcTemplate, 90, 0, 100, 5, 0, true, true);

        service.applyRetentionPolicy();

        verify(jdbcTemplate).queryForObject(
                org.mockito.ArgumentMatchers.contains("created_at < ?"),
                eq(Long.class),
                any(Object[].class));
        verify(jdbcTemplate, never()).update(anyString(), any(Object[].class));
    }

    @Test
    void deletesOnlyUpToConfiguredNumberOfBatchesWithStrictCutoff() {
        JdbcTemplate jdbcTemplate = org.mockito.Mockito.mock(JdbcTemplate.class);
        when(jdbcTemplate.queryForObject(anyString(), eq(Long.class), any(Object[].class)))
                .thenReturn(1_200L);
        when(jdbcTemplate.update(anyString(), any(Object[].class))).thenReturn(500, 500);
        LogRetentionService service = service(jdbcTemplate, 2, 4, 500, 2, 0, false, false);

        service.applyRetentionPolicy();

        verify(jdbcTemplate, org.mockito.Mockito.times(2)).update(
                org.mockito.ArgumentMatchers.contains("created_at < ?"),
                any(Object[].class));
        verify(jdbcTemplate, never()).update(
                org.mockito.ArgumentMatchers.contains("embedding IS NOT NULL"),
                any(Object[].class));
    }

    @Test
    void rejectsZeroRetentionDuration() {
        JdbcTemplate jdbcTemplate = org.mockito.Mockito.mock(JdbcTemplate.class);

        assertThrows(IllegalArgumentException.class,
                () -> service(jdbcTemplate, 0, 0, 500, 1, 0, false, true));
    }

    @Test
    void cutoffIsRecomputedForEachExecution() {
        JdbcTemplate jdbcTemplate = org.mockito.Mockito.mock(JdbcTemplate.class);
        when(jdbcTemplate.queryForObject(anyString(), eq(Long.class), any(Object[].class)))
                .thenReturn(0L);
        when(jdbcTemplate.update(anyString(), any(Object[].class))).thenReturn(0);
        LogRetentionService service = service(jdbcTemplate, 0, 1, 10, 1, 0, false, false);

        Instant before = Instant.now();
        service.applyRetentionPolicy();
        Instant after = Instant.now();

        org.mockito.ArgumentCaptor<Object[]> countArguments =
                org.mockito.ArgumentCaptor.forClass(Object[].class);
        verify(jdbcTemplate).queryForObject(anyString(), eq(Long.class), countArguments.capture());
        OffsetDateTime cutoff = (OffsetDateTime) countArguments.getValue()[0];
        assertTrue(cutoff.toInstant().isAfter(before.minusSeconds(3_601)));
        assertTrue(cutoff.toInstant().isBefore(after.minusSeconds(3_599)));

        org.mockito.ArgumentCaptor<Object[]> deleteArguments =
                org.mockito.ArgumentCaptor.forClass(Object[].class);
        verify(jdbcTemplate).update(anyString(), deleteArguments.capture());
        assertEquals(cutoff, deleteArguments.getValue()[0]);
    }

    private static LogRetentionService service(
            JdbcTemplate jdbcTemplate,
            int days,
            int hours,
            int batchSize,
            int maxBatches,
            long delayMillis,
            boolean dryRun,
            boolean completedOnly) {
        return new LogRetentionService(
                jdbcTemplate, days, hours, batchSize, maxBatches, delayMillis, dryRun, completedOnly);
    }
}
