package com.payment.replay.masking;

import com.payment.replay.config.MaskFieldConfig;

/**
 * Masks the beginning of the value and keeps the last N characters visible.
 *
 * Example (n=4):
 *   Input:  "1234567890123456"
 *   Output: "************3456"
 */
public final class KeepLastNStrategy implements MaskingStrategy {

    public static final String NAME = "KEEP_LAST_N";

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

        int maskLength = value.length() - n;
        StringBuilder masked = new StringBuilder(value.length());
        for (int i = 0; i < maskLength; i++) {
            masked.append(maskChar);
        }
        masked.append(value.substring(maskLength));
        return masked.toString();
    }

    @Override
    public String getStrategyName() {
        return NAME;
    }
}
