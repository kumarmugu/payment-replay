package com.payment.replay.exception;

/**
 * Thrown when XML masking operations fail.
 * This can occur due to malformed XML, unsupported masking strategies,
 * or issues applying mask patterns to field values.
 */
public class MaskingException extends PaymentReplayException {

    private static final String ERROR_CODE = "PRE-300";

    public MaskingException(String message) {
        super(ERROR_CODE, message);
    }

    public MaskingException(String message, Throwable cause) {
        super(ERROR_CODE, message, cause);
    }
}
