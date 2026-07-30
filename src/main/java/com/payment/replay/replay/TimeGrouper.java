package com.payment.replay.replay;

import com.payment.replay.config.ReplayConfig;
import com.payment.replay.model.ReplayMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Groups replay messages into time-based batches.
 *
 * Messages are grouped by configurable intervals (default: 60 seconds).
 * Within each batch, messages are ordered by their original timestamp.
 *
 * Example with 60-second interval:
 *   10:01:00 - 10:01:59 -> Batch "10:01"
 *   10:02:00 - 10:02:59 -> Batch "10:02"
 *
 * The batches are returned in chronological order for sequential replay.
 */
public final class TimeGrouper {

    private static final Logger log = LoggerFactory.getLogger(TimeGrouper.class);

    private static final DateTimeFormatter TIME_FORMAT = DateTimeFormatter.ofPattern("HH:mm:ss");

    private final int groupingIntervalSeconds;

    public TimeGrouper(ReplayConfig replayConfig) {
        this.groupingIntervalSeconds = replayConfig.getGroupingIntervalSeconds();
        log.info("TimeGrouper initialized with {}s interval", groupingIntervalSeconds);
    }

    /**
     * Groups messages into time-based batches.
     * Returns a LinkedHashMap to preserve chronological order.
     *
     * @param messages list of replay messages
     * @return ordered map of time key -> list of messages in that interval
     */
    public Map<String, List<ReplayMessage>> groupByTime(List<ReplayMessage> messages) {
        if (messages == null || messages.isEmpty()) {
            return Collections.emptyMap();
        }

        Map<String, List<ReplayMessage>> groups = new LinkedHashMap<>();

        for (ReplayMessage message : messages) {
            String timeKey = computeTimeKey(message.getTimestamp());
            groups.computeIfAbsent(timeKey, k -> new ArrayList<>()).add(message);
        }

        // Sort each group internally by timestamp
        for (List<ReplayMessage> group : groups.values()) {
            group.sort(Comparator.comparing(ReplayMessage::getTimestamp));
        }

        // Sort groups by key to ensure chronological order
        List<Map.Entry<String, List<ReplayMessage>>> sortedEntries = new ArrayList<>(groups.entrySet());
        sortedEntries.sort(Comparator.comparing(Map.Entry::getKey));

        Map<String, List<ReplayMessage>> sorted = new LinkedHashMap<>();
        for (Map.Entry<String, List<ReplayMessage>> entry : sortedEntries) {
            sorted.put(entry.getKey(), entry.getValue());
        }

        log.info("Grouped {} messages into {} time batches ({}s interval)",
                messages.size(), sorted.size(), groupingIntervalSeconds);

        return sorted;
    }

    /**
     * Computes the time key for a message based on its timestamp and the grouping interval.
     *
     * For a 60-second interval:
     *   "2026-07-28T10:01:23.456Z" -> "10:01"
     *
     * For a 30-second interval:
     *   "2026-07-28T10:01:23.456Z" -> "10:01:00"
     *   "2026-07-28T10:01:45.456Z" -> "10:01:30"
     *
     * @param timestamp the message timestamp string
     * @return time key representing the batch
     */
    String computeTimeKey(String timestamp) {
        if (timestamp == null || timestamp.isEmpty()) {
            return "00:00";
        }

        try {
            // Extract time portion from various timestamp formats
            String timePart = extractTimePart(timestamp);
            LocalTime time = parseTime(timePart);

            int totalSeconds = time.toSecondOfDay();
            int bucketStart = (totalSeconds / groupingIntervalSeconds) * groupingIntervalSeconds;

            LocalTime bucketTime = LocalTime.ofSecondOfDay(bucketStart);

            if (groupingIntervalSeconds >= 60) {
                return bucketTime.format(DateTimeFormatter.ofPattern("HH:mm"));
            } else {
                return bucketTime.format(TIME_FORMAT);
            }

        } catch (Exception e) {
            log.trace("Cannot parse timestamp '{}', using default key: {}", timestamp, e.getMessage());
            return "00:00";
        }
    }

    /**
     * Extracts the time portion from various timestamp formats.
     * Handles:
     *   "2026-07-28T10:01:23.456Z"
     *   "2026-07-28 10:01:23.456"
     *   "10:01:23.456"
     *   "10:01:23"
     */
    private String extractTimePart(String timestamp) {
        // If contains 'T', extract after it
        int tIndex = timestamp.indexOf('T');
        if (tIndex >= 0) {
            String after = timestamp.substring(tIndex + 1);
            // Remove timezone suffix
            int zIndex = after.indexOf('Z');
            if (zIndex >= 0) {
                after = after.substring(0, zIndex);
            }
            int plusIndex = after.indexOf('+');
            if (plusIndex >= 0) {
                after = after.substring(0, plusIndex);
            }
            return after;
        }

        // If contains space, extract after last space
        int spaceIndex = timestamp.lastIndexOf(' ');
        if (spaceIndex >= 0) {
            return timestamp.substring(spaceIndex + 1);
        }

        // Assume it's already a time string
        return timestamp;
    }

    /**
     * Parses a time string in HH:mm:ss or HH:mm:ss.SSS format.
     */
    private LocalTime parseTime(String timePart) {
        // Truncate milliseconds if present beyond 3 digits
        int dotIndex = timePart.indexOf('.');
        if (dotIndex >= 0) {
            String millis = timePart.substring(dotIndex + 1);
            if (millis.length() > 3) {
                timePart = timePart.substring(0, dotIndex + 4);
            }
        }

        try {
            return LocalTime.parse(timePart, DateTimeFormatter.ofPattern("HH:mm:ss.SSS"));
        } catch (DateTimeParseException e1) {
            try {
                return LocalTime.parse(timePart, TIME_FORMAT);
            } catch (DateTimeParseException e2) {
                return LocalTime.parse(timePart, DateTimeFormatter.ofPattern("HH:mm"));
            }
        }
    }

    /**
     * Returns the configured grouping interval in seconds.
     */
    public int getGroupingIntervalSeconds() {
        return groupingIntervalSeconds;
    }
}
