package com.payment.replay.masking;

import com.payment.replay.config.MaskFieldConfig;

/**
 * Replaces the entire value with the mask character.
 *
 * Example:
 *   Input:  "John Smith"
 *   Output: "XXXXXXXXXX" (using maskChar 'X')
 */
public final class FullMaskStrategy implements MaskingStrategy {

    public static final String NAME = "FULL_MASK";

    @Override
    public String mask(String value, MaskFieldConfig fieldConfig) {
        if (value == null || value.isEmpty()) {
            return value;
        }
        char maskChar = fieldConfig.getMaskChar();
        StringBuilder masked = new StringBuilder(value.length());
        for (int i = 0; i < value.length(); i++) {
            masked.append(maskChar);
        }
        return masked.toString();
    }

    @Override
    public String getStrategyName() {
        return NAME;
    }
}
