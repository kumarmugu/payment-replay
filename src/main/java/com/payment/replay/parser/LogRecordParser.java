package com.payment.replay.parser;

import com.payment.replay.config.AppConfig;
import com.payment.replay.exception.LogParsingException;
import com.payment.replay.model.LogRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;
import java.util.function.Consumer;
import java.util.regex.Pattern;

/**
 * Streaming log record parser that reads production log files line by line.
 * 
 * Designed for large files - never loads entire file into memory.
 * 
 * Log format:
 * <datetime>,mq,<direction>,<bank bic>,<switch name>,<Msg type>,<valid>,<instra id>,<MsgId>...<MQ name>...<XML Message>
 *
 * The "..." separator is used to delimit the queue name and XML payload sections
 * because these fields may contain commas.
 */
public final class LogRecordParser {

    private static final Logger log = LoggerFactory.getLogger(LogRecordParser.class);

    // The three-dot separator used in log format between metadata, queue name, and XML
    private static final String FIELD_SEPARATOR = "...";

    // Queue name pattern for filtering: <BANK>_REQUEST.TO.G3_<siteNo>
    private static final Pattern QUEUE_PATTERN = Pattern.compile("\\w+_REQUEST\\.TO\\.G3_\\d+");

    private final AppConfig config;

    public LogRecordParser(AppConfig config) {
        this.config = config;
    }

    /**
     * Streams and parses a log file, invoking the consumer for each matching record.
     * Only records matching the queue pattern AND bank list filter are passed through.
     *
     * This method processes the file line by line using a BufferedReader to handle
     * arbitrarily large files without excessive memory usage.
     *
     * @param filePath    path to the log file
     * @param consumer    callback for each valid, filtered LogRecord
     * @param onError     callback for records that fail parsing
     * @return number of records scanned (total lines, including non-matching)
     */
    public long parseFile(Path filePath, Consumer<LogRecord> consumer, Consumer<LogParsingException> onError) {
        String sourceFile = filePath.toString();
        long lineNumber = 0;
        Set<String> allowedBics = config.getAllowedBics();

        log.debug("Starting to parse file: {}", sourceFile);

        try (BufferedReader reader = Files.newBufferedReader(filePath, StandardCharsets.UTF_8)) {
            String line;
            while ((line = reader.readLine()) != null) {
                lineNumber++;

                if (line.trim().isEmpty()) {
                    continue;
                }

                try {
                    LogRecord record = parseLine(line, sourceFile, lineNumber);

                    if (record == null) {
                        continue;
                    }

                    // Filter: queue name must match the expected pattern
                    if (!matchesQueuePattern(record.getQueueName())) {
                        continue;
                    }

                    // Filter: bank BIC must be in allowed list
                    if (!allowedBics.contains(record.getBankBic())) {
                        continue;
                    }

                    consumer.accept(record);

                } catch (LogParsingException e) {
                    if (onError != null) {
                        onError.accept(e);
                    }
                    log.trace("Parse error at {}:{} - {}", sourceFile, lineNumber, e.getMessage());
                }
            }
        } catch (IOException e) {
            log.error("IO error reading file {}: {}", sourceFile, e.getMessage());
            throw new LogParsingException("Failed to read file: " + sourceFile, sourceFile, 0, e);
        }

        log.debug("Completed parsing file: {}. Lines scanned: {}", sourceFile, lineNumber);
        return lineNumber;
    }

    /**
     * Parses a single log line into a LogRecord.
     *
     * Expected format:
     * <datetime>,mq,<direction>,<bank bic>,<switch>,<MsgType>,<valid>,<instraId>,<MsgId>...<QueueName>...<XML>
     *
     * @param line       raw log line
     * @param sourceFile source file path for context
     * @param lineNumber line number for context
     * @return parsed LogRecord, or null if line is not an MQ record
     * @throws LogParsingException if the line appears to be an MQ record but is malformed
     */
    LogRecord parseLine(String line, String sourceFile, long lineNumber) {
        // Quick check: must contain "mq" marker and the field separator
        if (!line.contains(",mq,") || !line.contains(FIELD_SEPARATOR)) {
            return null;
        }

        // Split into three main parts: metadata...queueName...xmlPayload
        int firstSeparator = line.indexOf(FIELD_SEPARATOR);
        int secondSeparator = line.indexOf(FIELD_SEPARATOR, firstSeparator + FIELD_SEPARATOR.length());

        if (secondSeparator < 0) {
            throw new LogParsingException(
                    "Missing second separator in MQ log record", sourceFile, lineNumber);
        }

        String metadataPart = line.substring(0, firstSeparator);
        String queueName = line.substring(firstSeparator + FIELD_SEPARATOR.length(), secondSeparator).trim();
        String xmlPayload = line.substring(secondSeparator + FIELD_SEPARATOR.length()).trim();

        // Parse comma-separated metadata fields
        String[] fields = metadataPart.split(",", -1);

        if (fields.length < 9) {
            throw new LogParsingException(
                    "Insufficient metadata fields (expected 9, got " + fields.length + ")",
                    sourceFile, lineNumber);
        }

        // Validate "mq" marker at position 1
        if (!"mq".equalsIgnoreCase(fields[1].trim())) {
            return null;
        }

        return LogRecord.builder()
                .timestamp(fields[0].trim())
                .direction(fields[2].trim())
                .bankBic(fields[3].trim())
                .switchName(fields[4].trim())
                .messageType(fields[5].trim())
                .validFlag(fields[6].trim())
                .instructionId(fields[7].trim())
                .messageId(fields[8].trim())
                .queueName(queueName)
                .xmlPayload(xmlPayload)
                .sourceFile(sourceFile)
                .lineNumber(lineNumber)
                .build();
    }

    /**
     * Checks if a queue name matches the expected pattern: <BANK>_REQUEST.TO.G3_<siteNo>
     *
     * @param queueName queue name from log record
     * @return true if the queue name matches the filter pattern
     */
    boolean matchesQueuePattern(String queueName) {
        if (queueName == null || queueName.isEmpty()) {
            return false;
        }
        return QUEUE_PATTERN.matcher(queueName).matches();
    }
}
