package com.payment.replay.model;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * Represents a single error entry for the CSV error report.
 * Each failed record generates one ErrorEntry that is collected and written to the report.
 */
public final class ErrorEntry {

    private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS");

    private final LocalDateTime timestamp;
    private final String recordId;
    private final ErrorType errorType;
    private final String errorDescription;
    private final String sourceFile;
    private final long lineNumber;

    public ErrorEntry(LocalDateTime timestamp, String recordId, ErrorType errorType,
                      String errorDescription, String sourceFile, long lineNumber) {
        this.timestamp = timestamp;
        this.recordId = recordId;
        this.errorType = errorType;
        this.errorDescription = errorDescription;
        this.sourceFile = sourceFile;
        this.lineNumber = lineNumber;
    }

    /**
     * Convenience factory method for creating error entries with current timestamp.
     */
    public static ErrorEntry of(String recordId, ErrorType errorType,
                                String errorDescription, String sourceFile, long lineNumber) {
        return new ErrorEntry(LocalDateTime.now(), recordId, errorType, errorDescription, sourceFile, lineNumber);
    }

    public LocalDateTime getTimestamp() {
        return timestamp;
    }

    public String getRecordId() {
        return recordId;
    }

    public ErrorType getErrorType() {
        return errorType;
    }

    public String getErrorDescription() {
        return errorDescription;
    }

    public String getSourceFile() {
        return sourceFile;
    }

    public long getLineNumber() {
        return lineNumber;
    }

    /**
     * Converts this entry to a CSV-formatted line.
     * Escapes commas and quotes in description field.
     */
    public String toCsvLine() {
        String escapedDescription = escapeForCsv(errorDescription);
        return String.join(",",
                FORMATTER.format(timestamp),
                escapeForCsv(recordId),
                errorType.name(),
                escapedDescription,
                escapeForCsv(sourceFile),
                String.valueOf(lineNumber));
    }

    /**
     * CSV header line for error reports.
     */
    public static String csvHeader() {
        return "Timestamp,RecordID,ErrorType,ErrorDescription,SourceFile,LineNumber";
    }

    private String escapeForCsv(String value) {
        if (value == null) {
            return "";
        }
        if (value.contains(",") || value.contains("\"") || value.contains("\n")) {
            return "\"" + value.replace("\"", "\"\"") + "\"";
        }
        return value;
    }

    @Override
    public String toString() {
        return "ErrorEntry{" +
                "timestamp=" + timestamp +
                ", recordId='" + recordId + '\'' +
                ", errorType=" + errorType +
                ", errorDescription='" + errorDescription + '\'' +
                '}';
    }

    /**
     * Enumeration of error types for classification in error reports.
     */
    public enum ErrorType {
        LOG_PARSE_ERROR,
        XML_PARSE_ERROR,
        MASKING_ERROR,
        BANK_MAPPING_ERROR,
        MQ_CONNECTION_ERROR,
        MQ_SEND_ERROR,
        FILE_IO_ERROR,
        CONFIGURATION_ERROR,
        UNKNOWN_ERROR
    }
}
