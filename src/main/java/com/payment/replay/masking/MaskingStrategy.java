package com.payment.replay.masking;

import com.payment.replay.config.MaskFieldConfig;

/**
 * Strategy interface for applying data masking to field values.
 * Implementations define different masking algorithms (full mask, keep first N, etc.)
 *
 * New strategies can be added by:
 * 1. Implementing this interface
 * 2. Registering in MaskingStrategyFactory
 */
public interface MaskingStrategy {

    /**
     * Applies masking to the given value based on the field configuration.
     *
     * @param value       original field value to mask
     * @param fieldConfig configuration containing strategy parameters (maskChar, n, pattern, etc.)
     * @return masked value
     */
    String mask(String value, MaskFieldConfig fieldConfig);

    /**
     * Returns the strategy identifier (e.g., "FULL_MASK", "KEEP_LAST_N").
     */
    String getStrategyName();
}
