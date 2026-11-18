package tf.dpi.masking;

import java.util.List;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.stereotype.Component;

/**
 * Configuration holder for all masking rules applied to MOSIP logs.
 * <p>
 * Rules are general-purpose and not specific to any country or region.
 * Default rules cover common sensitive data patterns found in application logs.
 */
@Configuration
@ConfigurationProperties(prefix = "mosip.masking")
@Component
public class MaskingConfig {

    private List<MaskingRule> rules = List.of(
            new MaskingRule("ip-address-v4", "\\b(?:(?:25[0-5]|2[0-4][0-9]|[01]?[0-9][0-9]?)\\.){3}(?:25[0-5]|2[0-4][0-9]|[01]?[0-9][0-9]?)\\b", "[MASKED_IP]", true),
            new MaskingRule("ip-address-v6", "\\b(?:[0-9a-fA-F]{1,4}:){7}[0-9a-fA-F]{1,4}\\b", "[MASKED_IP]", true),
            new MaskingRule("email", "[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}", "[MASKED_EMAIL]", true),
            new MaskingRule("phone", "\\b\\d{3}[-.\\s]?\\d{3}[-.\\s]?\\d{4}\\b", "[MASKED_PHONE]", true),
            new MaskingRule("ssn", "\\b\\d{3}-\\d{2}-\\d{4}\\b", "[MASKED_SSN]", true),
            new MaskingRule("password", "(?i)(password|passwd|pwd|secret|api\\s*key|token)\\s*[:=]\\s*\\S+", "$1=[MASKED]", true),
            new MaskingRule("bearer-token", "(?i)Bearer\\s+[A-Za-z0-9._-]+", "Bearer [MASKED_TOKEN]", true),
            new MaskingRule("uuid", "\\b[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}\\b", "[MASKED_UUID]", true),
            new MaskingRule("url-query-token", "(?i)([?&](?:token|api[_-]?key|access[_-]?token|secret|password|pwd)=)[^&\\s]+", "$1[MASKED]", true)
    );

    public List<MaskingRule> getRules() {
        return rules;
    }

    public void setRules(List<MaskingRule> rules) {
        this.rules = rules;
    }
}
