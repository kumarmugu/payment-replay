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
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import java.util.function.Consumer;

/**
 * Streaming log record parser that reads production log files line by line.
 *
 * Designed for very large files - never loads entire file into memory.
 *
 * Actual log format (12 comma-separated fields, XML is the last field):
 *
 *   [0]  datetime (UTC)
 *   [1]  "mq"
 *   [2]  direction  (in / out)
 *   [3]  bank BIC
 *   [4]  switch name including site suffix, e.g. switch2UG3IPSSWITC1-MQ1
 *   [5]  message type, e.g. pacs.008.001.08 / admn.005.001.01
 *   [6]  "valid"
 *   [7]  instruction ID
 *   [8]  message ID
 *   [9]  "iso20022"
 *   [10] "raw"
 *   [11] XML payload (may contain commas — everything from field 11 to end of line)
 *
 * Filter criteria:
 *   direction == "in"
 *   AND message type starts with "pacs.008" OR "admn.005"
 *   AND bank BIC in configured bank-list
 *
 * There is NO queue name in the log. The destination queue is derived at processing
 * time from the bank BIC and the site number extracted from the switch name suffix
 * (-MQ1 → site 1, -MQ2 → site 2).
 */
public final class LogRecordParser {

    private static final Logger log = LoggerFactory.getLogger(LogRecordParser.class);

    /** Minimum number of comma-separated fields expected before the XML payload. */
    private static final int MIN_FIELDS = 12;

    /** Index of the XML payload field (everything from this index onward). */
    private static final int XML_FIELD_INDEX = 11;

    /** Message type prefixes that qualify a record for processing — ALL legs. */
    private static final Set<String> ALL_MSG_TYPES = new HashSet<>(Arrays.asList(
            "pacs.008", "admn.005", "pacs.002"
    ));

    private final AppConfig config;

    public LogRecordParser(AppConfig config) {
        this.config = config;
    }

    /**
     * Streams and parses a log file, invoking the consumer for each matching record.
     *
     * @param filePath       path to the log file
     * @param consumer       callback for each valid, filtered LogRecord
     * @param onError        callback for records that fail parsing (null = silently skip)
     * @param onSkippedBic   callback when a record is skipped due to BIC not in bank list (null = ignore)
     * @return total number of lines scanned (including blank and non-matching lines)
     */
    public long parseFile(Path filePath, Consumer<LogRecord> consumer,
                          Consumer<LogParsingException> onError,
                          Consumer<String> onSkippedBic) {
        String sourceFile = filePath.toString();
        long lineNumber = 0;
        Set<String> allowedBics = config.getAllowedBics();

        log.debug("Parsing file: {}", sourceFile);

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

                    // Filter: bank BIC must be in the configured allowed list
                    if (!allowedBics.contains(record.getBankBic())) {
                        if (onSkippedBic != null) {
                            onSkippedBic.accept(record.getBankBic());
                        }
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

        log.debug("Finished parsing {}. Lines scanned: {}", sourceFile, lineNumber);
        return lineNumber;
    }

    /**
     * Backwards-compatible overload without skipped-BIC callback.
     */
    public long parseFile(Path filePath, Consumer<LogRecord> consumer, Consumer<LogParsingException> onError) {
        return parseFile(filePath, consumer, onError, null);
    }

    /**
     * Parses a single log line into a LogRecord.
     *
     * Returns null for:
     *   - Lines that are not MQ records (no ",mq," marker)
     *   - Records where direction != "in"
     *   - Records whose message type is not pacs.008 or admn.005
     *
     * Throws LogParsingException for lines that look like MQ records but are malformed.
     *
     * @param line       raw log line
     * @param sourceFile source file path (for error context)
     * @param lineNumber line number (for error context)
     * @return parsed LogRecord, or null if line should be skipped
     */
    LogRecord parseLine(String line, String sourceFile, long lineNumber) {

        // Quick pre-filter: must contain the mq marker
        if (!line.contains(",mq,")) {
            return null;
        }

        // Split into exactly MIN_FIELDS parts; the last part captures the full XML
        // even if it contains commas, by limiting the split to MIN_FIELDS tokens.
        String[] fields = line.split(",", MIN_FIELDS);

        if (fields.length < MIN_FIELDS) {
            throw new LogParsingException(
                    "Insufficient fields (expected " + MIN_FIELDS + ", got " + fields.length + ")",
                    sourceFile, lineNumber);
        }

        // Validate fixed markers
        if (!"mq".equalsIgnoreCase(fields[1].trim())) {
            return null;
        }

        String direction  = fields[2].trim();
        String bankBic    = fields[3].trim();
        String switchName = fields[4].trim();
        String msgType    = fields[5].trim();
        String validFlag  = fields[6].trim();
        String instrId    = fields[7].trim();
        String msgId      = fields[8].trim();
        // fields[9]  == "iso20022"
        // fields[10] == "raw"
        String xmlPayload = fields[XML_FIELD_INDEX].trim();

        // Filter 1: only inbound records
        if (!"in".equalsIgnoreCase(direction)) {
            return null;
        }

        // Filter 2: only qualifying message types (pacs.008, admn.005, pacs.002)
        if (!isQualifyingMsgType(msgType)) {
            return null;
        }

        // Extract site number from switch name suffix: -MQ1 or -MQ2
        String siteNo = extractSiteNo(switchName);

        return LogRecord.builder()
                .timestamp(fields[0].trim())
                .direction(direction)
                .bankBic(bankBic)
                .switchName(switchName)
                .siteNo(siteNo)
                .messageType(msgType)
                .validFlag(validFlag)
                .instructionId(instrId)
                .messageId(msgId)
                .xmlPayload(xmlPayload)
                .sourceFile(sourceFile)
                .lineNumber(lineNumber)
                .build();
    }

    /**
     * Returns true if the message type qualifies for processing.
     * Matches on prefix: "pacs.008", "admn.005", or "pacs.002".
     */
    boolean isQualifyingMsgType(String msgType) {
        if (msgType == null || msgType.isEmpty()) {
            return false;
        }
        String lower = msgType.toLowerCase();
        for (String prefix : ALL_MSG_TYPES) {
            if (lower.startsWith(prefix)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Extracts the site number from the switch name.
     *
     * Switch name format: switch2UG3IPSSWITC1-MQ1 or switch2UG3IPSSWITCHI-MQ2
     * The site number is the digit after the last "-MQ" suffix.
     *
     * Defaults to "1" if the suffix is not present or not parseable.
     *
     * @param switchName the full switch name field
     * @return "1" or "2"
     */
    String extractSiteNo(String switchName) {
        if (switchName == null || switchName.isEmpty()) {
            return "1";
        }
        int mqIdx = switchName.toUpperCase().lastIndexOf("-MQ");
        if (mqIdx >= 0 && mqIdx + 3 < switchName.length()) {
            String siteStr = switchName.substring(mqIdx + 3).trim();
            // Take only leading digit(s)
            StringBuilder digits = new StringBuilder();
            for (char c : siteStr.toCharArray()) {
                if (Character.isDigit(c)) {
                    digits.append(c);
                } else {
                    break;
                }
            }
            if (digits.length() > 0) {
                return digits.toString();
            }
        }
        log.trace("Could not extract site number from switch name '{}', defaulting to 1", switchName);
        return "1";
    }
}
