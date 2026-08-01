package com.payment.replay.replay;

import com.payment.replay.config.AppConfig;
import com.payment.replay.config.ReplayConfig;
import com.payment.replay.exception.MqPublishException;
import com.payment.replay.logging.ErrorReporter;
import com.payment.replay.logging.MetricsCollector;
import com.payment.replay.mapping.QueueNameResolver;
import com.payment.replay.model.ErrorEntry;
import com.payment.replay.model.LegType;
import com.payment.replay.model.ProcessingMetrics;
import com.payment.replay.model.ReplayMessage;
import com.payment.replay.mq.MqPublisher;
import com.payment.replay.parser.SanitizedFileReader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/**
 * Orchestrates time-based replay of sanitized messages to IBM MQ.
 *
 * PARALLEL LEG REPLAY
 * -------------------
 * When replayLegs=both (default), Leg 1 and Leg 3 messages are replayed
 * in SEPARATE THREADS concurrently, each maintaining independent rate
 * limiting and timing.  This is critical for accurate time-based replay
 * because leg3 acknowledgements must arrive at MQ at the correct offset
 * relative to leg1 credit transfers.
 *
 * WALL-CLOCK-ALIGNED TIMING
 * --------------------------
 * Messages within a time bucket are dispatched as fast as the rate limiter
 * allows.  The orchestrator does NOT wait for wall-clock time to elapse
 * between buckets — the ordering is preserved but replay happens as fast
 * as MQ can absorb (up to maxMessagesPerSecond).  If real-time pacing is
 * needed in the future, a sleep-until-next-bucket delay can be inserted.
 *
 * PERFORMANCE TARGET: 250 TPS sustained for a full day's logs.
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
     */
    public void replay(String inputDirectory, ProcessingMetrics metrics) {
        ReplayConfig replayConfig = config.getReplayConfig();
        ReplayConfig.ReplayLegs legs = replayConfig.getReplayLegs();

        log.info("Starting replay from: {}  [legs={}]", inputDirectory, legs);

        // Read messages for each configured leg
        List<ReplayMessage> leg1Messages = new ArrayList<>();
        List<ReplayMessage> leg3Messages = new ArrayList<>();

        if (legs.includeLeg1()) {
            leg1Messages = fileReader.readAllMessages(inputDirectory, LegType.LEG1);
            log.info("Leg1 messages loaded: {}", leg1Messages.size());
        }
        if (legs.includeLeg3()) {
            leg3Messages = fileReader.readAllMessages(inputDirectory, LegType.LEG3);
            log.info("Leg3 messages loaded: {}", leg3Messages.size());
        }

        if (leg1Messages.isEmpty() && leg3Messages.isEmpty()) {
            log.warn("No messages found for replay in: {}", inputDirectory);
            return;
        }

        int maxPerSecond = replayConfig.getMaxMessagesPerSecond();

        // If both legs, run in parallel threads; otherwise single-threaded
        if (legs == ReplayConfig.ReplayLegs.BOTH && !leg1Messages.isEmpty() && !leg3Messages.isEmpty()) {
            replayParallel(leg1Messages, leg3Messages, maxPerSecond, metrics);
        } else if (!leg1Messages.isEmpty()) {
            replayLeg("Leg1", leg1Messages, maxPerSecond, metrics);
        } else {
            replayLeg("Leg3", leg3Messages, maxPerSecond, metrics);
        }

        mqPublisher.shutdown();
        log.info("Replay complete. Sent: {}, Failed: {}",
                metrics.getRecordsSentToMq(), metrics.getMqFailures());
    }

    /**
     * Replays Leg1 and Leg3 concurrently in separate threads.
     * Each leg gets its own rate-limited processing loop.
     */
    private void replayParallel(List<ReplayMessage> leg1, List<ReplayMessage> leg3,
                                int maxPerSecond, ProcessingMetrics metrics) {
        // Split rate limit: 70% leg1, 30% leg3 (leg1 typically has more volume)
        int leg1Rate = Math.max(1, (int) (maxPerSecond * 0.7));
        int leg3Rate = Math.max(1, maxPerSecond - leg1Rate);

        log.info("Parallel replay: Leg1 rate={}/s, Leg3 rate={}/s", leg1Rate, leg3Rate);

        ExecutorService executor = Executors.newFixedThreadPool(2);

        executor.submit(() -> replayLeg("Leg1", leg1, leg1Rate, metrics));
        executor.submit(() -> replayLeg("Leg3", leg3, leg3Rate, metrics));

        executor.shutdown();
        try {
            executor.awaitTermination(8, TimeUnit.HOURS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.warn("Parallel replay interrupted");
        }
    }

    /**
     * Replays a single leg's messages in time-bucket order with rate limiting.
     */
    private void replayLeg(String legName, List<ReplayMessage> messages,
                           int maxPerSecond, ProcessingMetrics metrics) {
        Map<String, List<ReplayMessage>> batches = timeGrouper.groupByTime(messages);
        log.info("[{}] {} messages grouped into {} batches", legName, messages.size(), batches.size());

        for (Map.Entry<String, List<ReplayMessage>> entry : batches.entrySet()) {
            String timeKey = entry.getKey();
            List<ReplayMessage> batch = entry.getValue();
            processBatch(legName, timeKey, batch, maxPerSecond, metrics);
        }
    }

    /**
     * Sends one batch of messages with per-second rate limiting.
     */
    private void processBatch(String legName, String timeKey, List<ReplayMessage> batch,
                              int maxPerSecond, ProcessingMetrics metrics) {
        int sentInSecond = 0;
        long secondStart = System.nanoTime();

        for (ReplayMessage msg : batch) {
            metrics.recordMinuteRead(timeKey);

            try {
                // Rate limit: sleep to fill out the second if needed
                if (sentInSecond >= maxPerSecond) {
                    long elapsedNs = System.nanoTime() - secondStart;
                    long remainingMs = 1000 - (elapsedNs / 1_000_000);
                    if (remainingMs > 0) {
                        Thread.sleep(remainingMs);
                    }
                    sentInSecond = 0;
                    secondStart = System.nanoTime();
                }

                mqPublisher.publish(msg);
                metrics.incrementRecordsSentToMq();
                metrics.recordMinuteSend(timeKey);
                sentInSecond++;

            } catch (MqPublishException e) {
                log.error("[{}] MQ send failed: {} → {} site {}: {}",
                        legName, msg.getMessageId(), msg.getQueueName(),
                        msg.getTargetSite(), e.getMessage());
                metrics.incrementMqFailures();
                metrics.recordMinuteFailure(timeKey);
                errorReporter.reportError(ErrorEntry.of(msg.getMessageId(),
                        ErrorEntry.ErrorType.MQ_SEND_ERROR, e.getMessage(),
                        msg.getSourceFile(), msg.getLineNumber()));

            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                log.warn("[{}] Replay interrupted in batch [{}]", legName, timeKey);
                return;

            } catch (Exception e) {
                metrics.incrementMqFailures();
                metrics.recordMinuteFailure(timeKey);
                errorReporter.reportError(ErrorEntry.of(msg.getMessageId(),
                        ErrorEntry.ErrorType.UNKNOWN_ERROR, e.getMessage(),
                        msg.getSourceFile(), msg.getLineNumber()));
            }
        }
    }
}
