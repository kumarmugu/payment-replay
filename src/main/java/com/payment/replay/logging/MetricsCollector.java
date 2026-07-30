package com.payment.replay.logging;

import com.payment.replay.config.AppConfig;
import com.payment.replay.model.ProcessingMetrics;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Map;

/**
 * Collects and reports processing metrics to the dedicated metrics.log.
 *
 * Uses a separate SLF4J logger named "metrics" that routes to the metrics
 * log appender defined in log4j2.xml.
 *
 * Reports include:
 * - Summary statistics (totals, throughput, duration)
 * - Per-minute breakdown (read/sent/failed counts)
 * - Application start/end times
 */
public final class MetricsCollector {

    private static final Logger log = LoggerFactory.getLogger(MetricsCollector.class);
    private static final Logger metricsLog = LoggerFactory.getLogger("metrics");
    private static final DateTimeFormatter TIMESTAMP_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS");

    private final AppConfig config;

    public MetricsCollector(AppConfig config) {
        this.config = config;
    }

    /**
     * Reports complete processing metrics to the metrics log.
     * Called at the end of each command execution.
     *
     * @param metrics the collected processing metrics
     */
    public void reportMetrics(ProcessingMetrics metrics) {
        if (!config.isMetricsEnabled()) {
            log.debug("Metrics reporting disabled");
            return;
        }

        reportHeader(metrics);
        reportSummary(metrics);
        reportPerMinuteStats(metrics);
        reportFooter(metrics);
    }

    /**
     * Writes the metrics header with execution context.
     */
    private void reportHeader(ProcessingMetrics metrics) {
        metricsLog.info("========================================");
        metricsLog.info("PROCESSING METRICS REPORT");
        metricsLog.info("========================================");
        metricsLog.info("Start Time:  {}", formatInstant(metrics.getStartTime()));
        metricsLog.info("End Time:    {}", formatInstant(metrics.getEndTime()));
        metricsLog.info("Duration:    {}", formatDuration(metrics.getDurationMs()));
        metricsLog.info("----------------------------------------");
    }

    /**
     * Writes summary statistics.
     */
    private void reportSummary(ProcessingMetrics metrics) {
        metricsLog.info("SUMMARY:");
        metricsLog.info("  Files Processed:        {}", metrics.getFilesProcessed());
        metricsLog.info("  Records Scanned:        {}", metrics.getRecordsScanned());
        metricsLog.info("  Records Matched:        {}", metrics.getRecordsMatched());
        metricsLog.info("  Records Masked:         {}", metrics.getRecordsMasked());
        metricsLog.info("  Records Written:        {}", metrics.getRecordsWritten());
        metricsLog.info("  Records Sent to MQ:     {}", metrics.getRecordsSentToMq());
        metricsLog.info("  MQ Failures:            {}", metrics.getMqFailures());
        metricsLog.info("  XML Failures:           {}", metrics.getXmlFailures());
        metricsLog.info("  Bank Mapping Failures:  {}", metrics.getBankMappingFailures());
        metricsLog.info("  Throughput:             {:.1f} records/sec", metrics.getThroughput());
        metricsLog.info("  Avg Latency:            {}ms/record", calculateAvgLatency(metrics));
        metricsLog.info("----------------------------------------");
    }

    /**
     * Writes per-minute statistics table.
     */
    private void reportPerMinuteStats(ProcessingMetrics metrics) {
        Map<String, ProcessingMetrics.MinuteMetrics> minuteStats = metrics.getMinuteMetrics();

        if (minuteStats.isEmpty()) {
            return;
        }

        metricsLog.info("PER-MINUTE STATISTICS:");
        metricsLog.info(String.format("  %-10s %-10s %-10s %-10s", "Time", "Read", "Sent", "Failed"));
        metricsLog.info("  ----------------------------------------");

        minuteStats.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .forEach(entry -> {
                    ProcessingMetrics.MinuteMetrics mm = entry.getValue();
                    metricsLog.info(String.format("  %-10s %-10d %-10d %-10d",
                            entry.getKey(), mm.getRead(), mm.getSent(), mm.getFailed()));
                });

        metricsLog.info("----------------------------------------");
    }

    /**
     * Writes the metrics report footer.
     */
    private void reportFooter(ProcessingMetrics metrics) {
        metricsLog.info("========================================");
        metricsLog.info("END OF METRICS REPORT");
        metricsLog.info("========================================");
    }

    /**
     * Formats an Instant to a human-readable timestamp string.
     */
    private String formatInstant(Instant instant) {
        if (instant == null) {
            return "N/A";
        }
        return LocalDateTime.ofInstant(instant, ZoneId.systemDefault()).format(TIMESTAMP_FORMAT);
    }

    /**
     * Formats a duration in milliseconds to a human-readable string.
     */
    private String formatDuration(long durationMs) {
        Duration duration = Duration.ofMillis(durationMs);
        long hours = duration.toHours();
        long minutes = duration.toMinutes() % 60;
        long seconds = (duration.toMillis() / 1000) % 60;
        long millis = duration.toMillis() % 1000;

        if (hours > 0) {
            return String.format("%dh %dm %ds %dms", hours, minutes, seconds, millis);
        } else if (minutes > 0) {
            return String.format("%dm %ds %dms", minutes, seconds, millis);
        } else if (seconds > 0) {
            return String.format("%ds %dms", seconds, millis);
        } else {
            return String.format("%dms", millis);
        }
    }

    /**
     * Calculates average processing latency per record.
     */
    private long calculateAvgLatency(ProcessingMetrics metrics) {
        long totalProcessed = metrics.getRecordsSentToMq() > 0
                ? metrics.getRecordsSentToMq()
                : metrics.getRecordsWritten();

        if (totalProcessed == 0) {
            return 0;
        }
        return metrics.getDurationMs() / totalProcessed;
    }
}
