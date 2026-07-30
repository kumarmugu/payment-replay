package com.payment.replay.masking;

import com.payment.replay.config.MaskFieldConfig;

/**
 * Keeps the first N characters visible and masks the remainder.
 *
 * Example (n=4):
 *   Input:  "1234567890"
 *   Output: "1234******"
 */
public final class KeepFirstNStrategy implements MaskingStrategy {

    public static final String NAME = "KEEP_FIRST_N";

    @Override
    public String mask(String value, MaskFieldConfig fieldConfig) {
        if (value == null || value.isEmpty()) {
            return value;
        }

        int n = fieldConfig.getN();
        char maskChar = fieldConfig.getMaskChar();

        if (n >= value.length()) {
            // Value is shorter than N, keep entire value unmasked
            return value;
        }

        StringBuilder masked = new StringBuilder(value.length());
        masked.append(value, 0, n);
        for (int i = n; i < value.length(); i++) {
            masked.append(maskChar);
        }
        return masked.toString();
    }

    @Override
    public String getStrategyName() {
        return NAME;
    }
}
