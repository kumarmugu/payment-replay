package com.payment.replay.replay;

import com.payment.replay.config.AppConfig;
import com.payment.replay.config.ReplayConfig;
import com.payment.replay.exception.MqPublishException;
import com.payment.replay.logging.ErrorReporter;
import com.payment.replay.logging.MetricsCollector;
import com.payment.replay.mapping.QueueNameResolver;
import com.payment.replay.model.ErrorEntry;
import com.payment.replay.model.ProcessingMetrics;
import com.payment.replay.model.ReplayMessage;
import com.payment.replay.mq.MqPublisher;
import com.payment.replay.parser.SanitizedFileReader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Map;

/**
 * Orchestrates time-based replay of sanitized messages to IBM MQ.
 *
 * Processing flow:
 * 1. Read sanitized files from configured directory
 * 2. Group messages by time interval
 * 3. For each time batch, send messages respecting rate limits
 * 4. Track metrics and report errors for failed sends
 *
 * Rate limiting ensures MQ is not overwhelmed (max 250 msgs/sec by default).
 * Messages within a batch are sent sequentially to preserve ordering.
 */
public final class ReplayOrchestrator {

    private static final Logger log = LoggerFactory.getLogger(ReplayOrchestrator.class);

    private final AppConfig config;
    private final SanitizedFileReader fileReader;
    private final TimeGrouper timeGrouper;
    private final MqPublisher mqPublisher;
    private final QueueNameResolver queueNameResolver;
    private final MetricsCollector metricsCollector;
    private final ErrorReporter errorReporter;

    public ReplayOrchestrator(AppConfig config,
                              SanitizedFileReader fileReader,
                              TimeGrouper timeGrouper,
                              MqPublisher mqPublisher,
                              QueueNameResolver queueNameResolver,
                              MetricsCollector metricsCollector,
                              ErrorReporter errorReporter) {
        this.config = config;
        this.fileReader = fileReader;
        this.timeGrouper = timeGrouper;
        this.mqPublisher = mqPublisher;
        this.queueNameResolver = queueNameResolver;
        this.metricsCollector = metricsCollector;
        this.errorReporter = errorReporter;
    }

    /**
     * Executes the full replay process.
     *
     * @param inputDirectory directory containing sanitized files to replay
     * @param metrics processing metrics tracker
     */
    public void replay(String inputDirectory, ProcessingMetrics metrics) {
        log.info("Starting replay from directory: {}", inputDirectory);

        // Step 1: Read all sanitized messages
        List<ReplayMessage> messages = fileReader.readAllMessages(inputDirectory);

        if (messages.isEmpty()) {
            log.warn("No messages found in replay directory: {}", inputDirectory);
            return;
        }

        log.info("Loaded {} messages for replay", messages.size());

        // Step 2: Group by time
        Map<String, List<ReplayMessage>> batches = timeGrouper.groupByTime(messages);

        log.info("Messages grouped into {} time batches", batches.size());

        // Step 3: Process each batch
        ReplayConfig replayConfig = config.getReplayConfig();
        int maxPerSecond = replayConfig.getMaxMessagesPerSecond();

        for (Map.Entry<String, List<ReplayMessage>> entry : batches.entrySet()) {
            String timeKey = entry.getKey();
            List<ReplayMessage> batch = entry.getValue();

            log.info("Processing batch [{}]: {} messages", timeKey, batch.size());
            processBatch(timeKey, batch, maxPerSecond, metrics);
        }

        // Step 4: Shutdown MQ connections
        mqPublisher.shutdown();

        log.info("Replay complete. Total sent: {}, Total failed: {}",
                metrics.getRecordsSentToMq(), metrics.getMqFailures());
    }

    /**
     * Processes a single time batch, sending messages with rate limiting.
     *
     * @param timeKey      the batch time key (e.g., "10:01")
     * @param batch        messages in this time window
     * @param maxPerSecond maximum messages to send per second
     * @param metrics      metrics tracker
     */
    private void processBatch(String timeKey, List<ReplayMessage> batch,
                              int maxPerSecond, ProcessingMetrics metrics) {
        int sentInSecond = 0;
        long secondStartTime = System.currentTimeMillis();

        for (ReplayMessage message : batch) {
            metrics.recordMinuteRead(timeKey);

            try {
                // Rate limiting: if we've hit the per-second limit, wait
                if (sentInSecond >= maxPerSecond) {
                    long elapsed = System.currentTimeMillis() - secondStartTime;
                    if (elapsed < 1000) {
                        long sleepMs = 1000 - elapsed;
                        log.trace("Rate limit reached, sleeping {}ms", sleepMs);
                        Thread.sleep(sleepMs);
                    }
                    sentInSecond = 0;
                    secondStartTime = System.currentTimeMillis();
                }

                // Send message
                mqPublisher.publish(message);

                metrics.incrementRecordsSentToMq();
                metrics.recordMinuteSend(timeKey);
                sentInSecond++;

            } catch (MqPublishException e) {
                log.error("Failed to send message {} to queue {} on site {}: {}",
                        message.getMessageId(), message.getQueueName(),
                        message.getTargetSite(), e.getMessage());

                metrics.incrementMqFailures();
                metrics.recordMinuteFailure(timeKey);

                errorReporter.reportError(ErrorEntry.of(
                        message.getMessageId(),
                        ErrorEntry.ErrorType.MQ_SEND_ERROR,
                        e.getMessage(),
                        message.getSourceFile(),
                        message.getLineNumber()));

                // Continue processing - don't fail the entire batch

            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                log.warn("Replay interrupted during batch [{}]", timeKey);
                return;

            } catch (Exception e) {
                log.error("Unexpected error sending message {}: {}", message.getMessageId(), e.getMessage());
                metrics.incrementMqFailures();
                metrics.recordMinuteFailure(timeKey);

                errorReporter.reportError(ErrorEntry.of(
                        message.getMessageId(),
                        ErrorEntry.ErrorType.UNKNOWN_ERROR,
                        e.getMessage(),
                        message.getSourceFile(),
                        message.getLineNumber()));
            }
        }
    }
}
