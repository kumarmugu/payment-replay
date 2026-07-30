package com.payment.replay.config;

/**
 * Replay-specific configuration controlling time-based grouping and rate limiting.
 */
public final class ReplayConfig {

    private final int groupingIntervalSeconds;
    private final int maxMessagesPerSecond;
    private final String inputDirectory;

    public ReplayConfig(int groupingIntervalSeconds, int maxMessagesPerSecond, String inputDirectory) {
        this.groupingIntervalSeconds = groupingIntervalSeconds;
        this.maxMessagesPerSecond = maxMessagesPerSecond;
        this.inputDirectory = inputDirectory;
    }

    /**
     * Time interval in seconds for grouping messages.
     * Default is 60 (one minute batches).
     */
    public int getGroupingIntervalSeconds() {
        return groupingIntervalSeconds;
    }

    /**
     * Maximum messages to send per second to avoid overwhelming MQ.
     * Default is 250.
     */
    public int getMaxMessagesPerSecond() {
        return maxMessagesPerSecond;
    }

    /**
     * Input directory containing sanitized files to replay.
     */
    public String getInputDirectory() {
        return inputDirectory;
    }

    @Override
    public String toString() {
        return "ReplayConfig{" +
                "groupingIntervalSeconds=" + groupingIntervalSeconds +
                ", maxMessagesPerSecond=" + maxMessagesPerSecond +
                ", inputDirectory='" + inputDirectory + '\'' +
                '}';
    }
}
