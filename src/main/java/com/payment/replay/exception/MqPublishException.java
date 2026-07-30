package com.payment.replay.exception;

/**
 * Thrown when publishing a message to IBM MQ fails.
 * Contains details about the target queue and site to aid troubleshooting.
 */
public class MqPublishException extends PaymentReplayException {

    private static final String ERROR_CODE = "PRE-500";

    private final String queueName;
    private final int siteNumber;

    public MqPublishException(String message, String queueName, int siteNumber) {
        super(ERROR_CODE, message);
        this.queueName = queueName;
        this.siteNumber = siteNumber;
    }

    public MqPublishException(String message, String queueName, int siteNumber, Throwable cause) {
        super(ERROR_CODE, message, cause);
        this.queueName = queueName;
        this.siteNumber = siteNumber;
    }

    public String getQueueName() {
        return queueName;
    }

    public int getSiteNumber() {
        return siteNumber;
    }
}
