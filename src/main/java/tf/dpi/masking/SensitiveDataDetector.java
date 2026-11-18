package tf.dpi.masking;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Detector that identifies sensitive patterns in log text and applies masking rules.
 * <p>
 * Rules are applied in order; earlier rules may affect later matches.
 */
@Component
public class SensitiveDataDetector {

    private static final Logger log = LoggerFactory.getLogger(SensitiveDataDetector.class);
    private final ConcurrentMap<String, Pattern> compiledPatterns = new ConcurrentHashMap<>();

    public String mask(String text, MaskingRule rule) {
        if (text == null || rule == null || !rule.isEnabled()) {
            return text;
        }
        Pattern pattern = compiledPatterns.computeIfAbsent(rule.getPattern(), Pattern::compile);
        Matcher matcher = pattern.matcher(text);
        String masked = matcher.replaceAll(rule.getReplacement());
        if (!masked.equals(text)) {
            log.debug("Applied masking rule '{}'", rule.getName());
        }
        return masked;
    }

    public String mask(List<MaskingRule> rules, String text) {
        String result = text;
        for (MaskingRule rule : rules) {
            result = mask(result, rule);
        }
        return result;
    }

    int compiledPatternCount() {
        return compiledPatterns.size();
    }
}
