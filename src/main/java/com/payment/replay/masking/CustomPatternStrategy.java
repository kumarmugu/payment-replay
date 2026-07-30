package com.payment.replay.masking;

import com.payment.replay.config.MaskFieldConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

/**
 * Applies masking using a custom regex pattern and replacement string.
 *
 * The pattern and replacement are defined in the field configuration.
 *
 * Example:
 *   pattern:     "(\d{4})\d{8}(\d{4})"
 *   replacement: "$1********$2"
 *   Input:       "1234567890123456"
 *   Output:      "1234********3456"
 */
public final class CustomPatternStrategy implements MaskingStrategy {

    private static final Logger log = LoggerFactory.getLogger(CustomPatternStrategy.class);

    public static final String NAME = "CUSTOM_PATTERN";

    @Override
    public String mask(String value, MaskFieldConfig fieldConfig) {
        if (value == null || value.isEmpty()) {
            return value;
        }

        String pattern = fieldConfig.getPattern();
        String replacement = fieldConfig.getReplacement();

        if (pattern == null || pattern.isEmpty()) {
            log.warn("Custom pattern strategy configured without a pattern for path: {}", fieldConfig.getPath());
            // Fall back to full mask behavior
            char maskChar = fieldConfig.getMaskChar();
            StringBuilder masked = new StringBuilder(value.length());
            for (int i = 0; i < value.length(); i++) {
                masked.append(maskChar);
            }
            return masked.toString();
        }

        if (replacement == null) {
            replacement = "";
        }

        try {
            Pattern compiledPattern = Pattern.compile(pattern);
            return compiledPattern.matcher(value).replaceAll(replacement);
        } catch (PatternSyntaxException e) {
            log.error("Invalid regex pattern '{}' for field path '{}': {}",
                    pattern, fieldConfig.getPath(), e.getMessage());
            // On regex failure, fall back to full mask for safety
            char maskChar = fieldConfig.getMaskChar();
            StringBuilder masked = new StringBuilder(value.length());
            for (int i = 0; i < value.length(); i++) {
                masked.append(maskChar);
            }
            return masked.toString();
        }
    }

    @Override
    public String getStrategyName() {
        return NAME;
    }
}
