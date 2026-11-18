package tf.dpi.masking;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;

import org.junit.jupiter.api.Test;

class SensitiveDataDetectorTest {

    @Test
    void masksUrlQuerySecretsWithoutInvalidReplacementGroup() {
        SensitiveDataDetector detector = new SensitiveDataDetector();
        MaskingRule rule = new MaskingRule(
                "url-query-token",
                "(?i)([?&](?:token|api[_-]?key|access[_-]?token|secret|password|pwd)=)[^&\\s]+",
                "$1[MASKED]",
                true);

        assertEquals("https://example.test/path?token=[MASKED]&mode=read",
                detector.mask("https://example.test/path?token=secret-value&mode=read", rule));
    }

    @Test
    void reusesCompiledPatternsWhenMaskingManyEntries() {
        SensitiveDataDetector detector = new SensitiveDataDetector();
        MaskingRule email = new MaskingRule(
                "email", "[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}", "[MASKED_EMAIL]", true);

        assertEquals("[MASKED_EMAIL]", detector.mask("user@example.test", email));
        assertEquals("[MASKED_EMAIL]", detector.mask("user@example.test", email));
        assertEquals(1, detector.compiledPatternCount());
    }

    @Test
    void listMaskUsesConfiguredRulesInOrder() {
        SensitiveDataDetector detector = new SensitiveDataDetector();
        List<MaskingRule> rules = List.of(
                new MaskingRule("token", "(?i)(token=)[^&\\s]+", "$1[MASKED]", true));

        assertEquals("token=[MASKED]", detector.mask(rules, "token=abc123"));
    }
}
