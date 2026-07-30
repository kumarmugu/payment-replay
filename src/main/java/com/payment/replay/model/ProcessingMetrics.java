package com.payment.replay.model;

import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Thread-safe metrics collector for tracking processing statistics.
 * Uses atomic counters to support concurrent access during multi-threaded processing.
 */
public final class ProcessingMetrics {

    private final Instant startTime;
    private volatile Instant endTime;

    private final AtomicLong filesProcessed = new AtomicLong(0);
    private final AtomicLong recordsScanned = new AtomicLong(0);
    private final AtomicLong recordsMatched = new AtomicLong(0);
    private final AtomicLong recordsMasked = new AtomicLong(0);
    private final AtomicLong recordsSentToMq = new AtomicLong(0);
    private final AtomicLong mqFailures = new AtomicLong(0);
    private final AtomicLong xmlFailures = new AtomicLong(0);
    private final AtomicLong bankMappingFailures = new AtomicLong(0);
    private final AtomicLong recordsWritten = new AtomicLong(0);

    // Per-minute metrics: key = "HH:mm" format
    private final ConcurrentHashMap<String, MinuteMetrics> minuteMetrics = new ConcurrentHashMap<>();

    public ProcessingMetrics() {
        this.startTime = Instant.now();
    }

    public void markComplete() {
        this.endTime = Instant.now();
    }

    // --- Increment methods ---

    public void incrementFilesProcessed() {
        filesProcessed.incrementAndGet();
    }

    public void incrementRecordsScanned() {
        recordsScanned.incrementAndGet();
    }

    public void incrementRecordsMatched() {
        recordsMatched.incrementAndGet();
    }

    public void incrementRecordsMasked() {
        recordsMasked.incrementAndGet();
    }

    public void incrementRecordsSentToMq() {
        recordsSentToMq.incrementAndGet();
    }

    public void incrementMqFailures() {
        mqFailures.incrementAndGet();
    }

    public void incrementXmlFailures() {
        xmlFailures.incrementAndGet();
    }

    public void incrementBankMappingFailures() {
        bankMappingFailures.incrementAndGet();
    }

    public void incrementRecordsWritten() {
        recordsWritten.incrementAndGet();
    }

    // --- Per-minute tracking ---

    /**
     * Records a successful message send for a given minute key.
     *
     * @param minuteKey time key in "HH:mm" format
     */
    public void recordMinuteSend(String minuteKey) {
        minuteMetrics.computeIfAbsent(minuteKey, k -> new MinuteMetrics()).incrementSent();
    }

    /**
     * Records a read operation for a given minute key.
     *
     * @param minuteKey time key in "HH:mm" format
     */
    public void recordMinuteRead(String minuteKey) {
        minuteMetrics.computeIfAbsent(minuteKey, k -> new MinuteMetrics()).incrementRead();
    }

    /**
     * Records a failed send for a given minute key.
     *
     * @param minuteKey time key in "HH:mm" format
     */
    public void recordMinuteFailure(String minuteKey) {
        minuteMetrics.computeIfAbsent(minuteKey, k -> new MinuteMetrics()).incrementFailed();
    }

    // --- Getters ---

    public Instant getStartTime() {
        return startTime;
    }

    public Instant getEndTime() {
        return endTime;
    }

    public long getFilesProcessed() {
        return filesProcessed.get();
    }

    public long getRecordsScanned() {
        return recordsScanned.get();
    }

    public long getRecordsMatched() {
        return recordsMatched.get();
    }

    public long getRecordsMasked() {
        return recordsMasked.get();
    }

    public long getRecordsSentToMq() {
        return recordsSentToMq.get();
    }

    public long getMqFailures() {
        return mqFailures.get();
    }

    public long getXmlFailures() {
        return xmlFailures.get();
    }

    public long getBankMappingFailures() {
        return bankMappingFailures.get();
    }

    public long getRecordsWritten() {
        return recordsWritten.get();
    }

    public Map<String, MinuteMetrics> getMinuteMetrics() {
        return minuteMetrics;
    }

    /**
     * Calculates processing duration in milliseconds.
     *
     * @return duration in ms, or time elapsed since start if not yet complete
     */
    public long getDurationMs() {
        Instant end = this.endTime != null ? this.endTime : Instant.now();
        return end.toEpochMilli() - startTime.toEpochMilli();
    }

    /**
     * Calculates throughput in records per second.
     */
    public double getThroughput() {
        long durationSeconds = getDurationMs() / 1000;
        if (durationSeconds == 0) {
            return 0;
        }
        long totalProcessed = recordsSentToMq.get() > 0 ? recordsSentToMq.get() : recordsWritten.get();
        return (double) totalProcessed / durationSeconds;
    }

    @Override
    public String toString() {
        return "ProcessingMetrics{" +
                "filesProcessed=" + filesProcessed.get() +
                ", recordsScanned=" + recordsScanned.get() +
                ", recordsMatched=" + recordsMatched.get() +
                ", recordsMasked=" + recordsMasked.get() +
                ", recordsSentToMq=" + recordsSentToMq.get() +
                ", mqFailures=" + mqFailures.get() +
                ", durationMs=" + getDurationMs() +
                '}';
    }

    /**
     * Per-minute metrics bucket tracking reads, sends, and failures.
     */
    public static final class MinuteMetrics {
        private final AtomicLong read = new AtomicLong(0);
        private final AtomicLong sent = new AtomicLong(0);
        private final AtomicLong failed = new AtomicLong(0);

        public void incrementRead() {
            read.incrementAndGet();
        }

        public void incrementSent() {
            sent.incrementAndGet();
        }

        public void incrementFailed() {
            failed.incrementAndGet();
        }

        public long getRead() {
            return read.get();
        }

        public long getSent() {
            return sent.get();
        }

        public long getFailed() {
            return failed.get();
        }

        @Override
        public String toString() {
            return String.format("Read=%d, Sent=%d, Failed=%d", read.get(), sent.get(), failed.get());
        }
    }
}
