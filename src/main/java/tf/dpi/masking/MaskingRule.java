package tf.dpi.masking;

/**
 * Representation of a single masking rule: a regex pattern, its replacement,
 * and whether the rule is enabled.
 */
public class MaskingRule {

    private String name;
    private String pattern;
    private String replacement;
    private boolean enabled;

    public MaskingRule() {
        this.enabled = true;
    }

    public MaskingRule(String name, String pattern, String replacement, boolean enabled) {
        this.name = name;
        this.pattern = pattern;
        this.replacement = replacement;
        this.enabled = enabled;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getPattern() {
        return pattern;
    }

    public void setPattern(String pattern) {
        this.pattern = pattern;
    }

    public String getReplacement() {
        return replacement;
    }

    public void setReplacement(String replacement) {
        this.replacement = replacement;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }
}
