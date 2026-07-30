package com.payment.replay.exception;

/**
 * Base exception for all application-specific exceptions.
 * All custom exceptions in the payment replay tool extend this class.
 */
public class PaymentReplayException extends RuntimeException {

    private final String errorCode;

    public PaymentReplayException(String message) {
        super(message);
        this.errorCode = "PRE-000";
    }

    public PaymentReplayException(String message, Throwable cause) {
        super(message, cause);
        this.errorCode = "PRE-000";
    }

    public PaymentReplayException(String errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
    }

    public PaymentReplayException(String errorCode, String message, Throwable cause) {
        super(message, cause);
        this.errorCode = errorCode;
    }

    public String getErrorCode() {
        return errorCode;
    }
}
