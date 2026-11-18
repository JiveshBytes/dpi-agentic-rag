package tf.dpi.masking;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import tf.dpi.model.LogEntry;

class LogMaskingServiceTest {

    @Test
    void masksInParallelWhilePreservingInputOrder() {
        MaskingConfig config = new MaskingConfig();
        config.setRules(List.of(new MaskingRule(
                "email", "[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}",
                "[MASKED_EMAIL]", true)));
        LogMaskingService service = new LogMaskingService(new SensitiveDataDetector(), config, 4);
        List<LogEntry> input = new ArrayList<>();
        for (int i = 0; i < 101; i++) {
            input.add(new LogEntry(null, null, "service", "INFO", Instant.EPOCH,
                    "entry-" + i + " user@example.test", null, null, Map.of()));
        }

        List<LogEntry> masked = service.mask(input);

        assertEquals(input.size(), masked.size());
        for (int i = 0; i < masked.size(); i++) {
            assertTrue(masked.get(i).getMessage().startsWith("entry-" + i + " "));
            assertTrue(masked.get(i).getMessage().endsWith("[MASKED_EMAIL]"));
        }
    }

    @Test
    void returnsEmptyForEmptyInput() {
        LogMaskingService service = new LogMaskingService(
                new SensitiveDataDetector(), new MaskingConfig(), 4);

        assertTrue(service.mask(List.of()).isEmpty());
    }
}
