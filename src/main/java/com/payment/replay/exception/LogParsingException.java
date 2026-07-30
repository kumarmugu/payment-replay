package com.payment.replay.exception;

/**
 * Thrown when a log line cannot be parsed according to the expected format.
 * Contains context about which file and line caused the failure.
 */
public class LogParsingException extends PaymentReplayException {

    private static final String ERROR_CODE = "PRE-200";

    private final String sourceFile;
    private final long lineNumber;

    public LogParsingException(String message, String sourceFile, long lineNumber) {
        super(ERROR_CODE, message);
        this.sourceFile = sourceFile;
        this.lineNumber = lineNumber;
    }

    public LogParsingException(String message, String sourceFile, long lineNumber, Throwable cause) {
        super(ERROR_CODE, message, cause);
        this.sourceFile = sourceFile;
        this.lineNumber = lineNumber;
    }

    public String getSourceFile() {
        return sourceFile;
    }

    public long getLineNumber() {
        return lineNumber;
    }
}
