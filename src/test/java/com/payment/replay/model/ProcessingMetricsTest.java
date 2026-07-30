package com.payment.replay.model;

import org.junit.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class ProcessingMetricsTest {

    @Test
    public void shouldTrackCountersCorrectly() {
        ProcessingMetrics metrics = new ProcessingMetrics();

        metrics.incrementFilesProcessed();
        metrics.incrementFilesProcessed();
        metrics.incrementRecordsScanned();
        metrics.incrementRecordsMatched();
        metrics.incrementRecordsMasked();
        metrics.incrementRecordsSentToMq();

        assertThat(metrics.getFilesProcessed()).isEqualTo(2);
        assertThat(metrics.getRecordsScanned()).isEqualTo(1);
        assertThat(metrics.getRecordsMatched()).isEqualTo(1);
        assertThat(metrics.getRecordsMasked()).isEqualTo(1);
        assertThat(metrics.getRecordsSentToMq()).isEqualTo(1);
    }

    @Test
    public void shouldTrackPerMinuteMetrics() {
        ProcessingMetrics metrics = new ProcessingMetrics();

        metrics.recordMinuteRead("10:01");
        metrics.recordMinuteRead("10:01");
        metrics.recordMinuteSend("10:01");
        metrics.recordMinuteFailure("10:01");

        metrics.recordMinuteRead("10:02");
        metrics.recordMinuteSend("10:02");

        assertThat(metrics.getMinuteMetrics()).hasSize(2);

        ProcessingMetrics.MinuteMetrics minute1 = metrics.getMinuteMetrics().get("10:01");
        assertThat(minute1.getRead()).isEqualTo(2);
        assertThat(minute1.getSent()).isEqualTo(1);
        assertThat(minute1.getFailed()).isEqualTo(1);
    }

    @Test
    public void shouldCalculateDuration() throws InterruptedException {
        ProcessingMetrics metrics = new ProcessingMetrics();
        Thread.sleep(50);
        metrics.markComplete();

        assertThat(metrics.getDurationMs()).isGreaterThanOrEqualTo(40);
    }

    @Test
    public void shouldStartWithZeroCounters() {
        ProcessingMetrics metrics = new ProcessingMetrics();

        assertThat(metrics.getFilesProcessed()).isEqualTo(0);
        assertThat(metrics.getRecordsScanned()).isEqualTo(0);
        assertThat(metrics.getRecordsSentToMq()).isEqualTo(0);
        assertThat(metrics.getMqFailures()).isEqualTo(0);
    }

    @Test
    public void shouldBeThreadSafe() throws InterruptedException {
        ProcessingMetrics metrics = new ProcessingMetrics();

        Thread t1 = new Thread(() -> {
            for (int i = 0; i < 1000; i++) {
                metrics.incrementRecordsScanned();
            }
        });

        Thread t2 = new Thread(() -> {
            for (int i = 0; i < 1000; i++) {
                metrics.incrementRecordsScanned();
            }
        });

        t1.start();
        t2.start();
        t1.join();
        t2.join();

        assertThat(metrics.getRecordsScanned()).isEqualTo(2000);
    }
}
