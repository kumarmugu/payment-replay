package com.payment.replay.exception;

/**
 * Thrown when configuration loading or validation fails.
 * This includes invalid YAML files, missing required fields, or inaccessible config paths.
 */
public class ConfigurationException extends PaymentReplayException {

    private static final String ERROR_CODE = "PRE-100";

    public ConfigurationException(String message) {
        super(ERROR_CODE, message);
    }

    public ConfigurationException(String message, Throwable cause) {
        super(ERROR_CODE, message, cause);
    }
}
