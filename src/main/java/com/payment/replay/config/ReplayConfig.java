package com.payment.replay.config;

/**
 * Replay-specific configuration controlling time-based grouping, rate limiting,
 * leg selection, and thread pool sizing.
 */
public final class ReplayConfig {

    /** Valid values for {@link #replayLegs}. */
    public enum ReplayLegs {
        LEG1, LEG3, BOTH;

        public static ReplayLegs from(String value) {
            if (value == null) return BOTH;
            switch (value.trim().toLowerCase()) {
                case "leg1": return LEG1;
                case "leg3": return LEG3;
                default:     return BOTH;
            }
        }

        public boolean includeLeg1() { return this == LEG1 || this == BOTH; }
        public boolean includeLeg3() { return this == LEG3 || this == BOTH; }
    }

    private final int groupingIntervalSeconds;
    private final int maxMessagesPerSecond;
    private final String inputDirectory;
    private final ReplayLegs replayLegs;

    public ReplayConfig(int groupingIntervalSeconds, int maxMessagesPerSecond,
                        String inputDirectory, ReplayLegs replayLegs) {
        this.groupingIntervalSeconds = groupingIntervalSeconds;
        this.maxMessagesPerSecond    = maxMessagesPerSecond;
        this.inputDirectory          = inputDirectory;
        this.replayLegs              = replayLegs != null ? replayLegs : ReplayLegs.BOTH;
    }

    /** Backwards-compatible 3-arg constructor — defaults to BOTH legs. */
    public ReplayConfig(int groupingIntervalSeconds, int maxMessagesPerSecond, String inputDirectory) {
        this(groupingIntervalSeconds, maxMessagesPerSecond, inputDirectory, ReplayLegs.BOTH);
    }

    public int          getGroupingIntervalSeconds() { return groupingIntervalSeconds; }
    public int          getMaxMessagesPerSecond()     { return maxMessagesPerSecond; }
    public String       getInputDirectory()           { return inputDirectory; }
    public ReplayLegs   getReplayLegs()               { return replayLegs; }

    @Override
    public String toString() {
        return "ReplayConfig{groupingIntervalSeconds=" + groupingIntervalSeconds
                + ", maxMessagesPerSecond=" + maxMessagesPerSecond
                + ", inputDirectory='" + inputDirectory + '\''
                + ", replayLegs=" + replayLegs + '}';
    }
}
