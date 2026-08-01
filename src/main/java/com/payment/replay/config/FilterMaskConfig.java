package com.payment.replay.config;

/**
 * Configuration specific to the filter-mask command:
 * output file suffixes for each leg, parallel processing thread count,
 * and writer queue size for backpressure control.
 */
public final class FilterMaskConfig {

    private final String leg1FileSuffix;
    private final String leg3FileSuffix;
    private final int    fileProcessingThreads;
    private final int    writerQueueSize;

    public FilterMaskConfig(String leg1FileSuffix, String leg3FileSuffix,
                            int fileProcessingThreads, int writerQueueSize) {
        this.leg1FileSuffix        = leg1FileSuffix != null        ? leg1FileSuffix        : "_leg1";
        this.leg3FileSuffix        = leg3FileSuffix != null        ? leg3FileSuffix        : "_leg3";
        this.fileProcessingThreads = fileProcessingThreads > 0     ? fileProcessingThreads : 4;
        this.writerQueueSize       = writerQueueSize > 0           ? writerQueueSize       : 2000;
    }

    /** Suffix added to output filename for Leg 1 records (pacs.008, admn.005). */
    public String getLeg1FileSuffix()       { return leg1FileSuffix; }

    /** Suffix added to output filename for Leg 3 records (pacs.002). */
    public String getLeg3FileSuffix()       { return leg3FileSuffix; }

    /** Number of files to process concurrently. */
    public int    getFileProcessingThreads() { return fileProcessingThreads; }

    /** Internal bounded queue capacity between parser and writer threads. */
    public int    getWriterQueueSize()       { return writerQueueSize; }

    /**
     * Returns the appropriate suffix for the given leg type string.
     * @param legType "LEG1" or "LEG3"
     */
    public String suffixForLeg(String legType) {
        return "LEG3".equalsIgnoreCase(legType) ? leg3FileSuffix : leg1FileSuffix;
    }

    @Override
    public String toString() {
        return "FilterMaskConfig{leg1='" + leg1FileSuffix + "', leg3='" + leg3FileSuffix
                + "', threads=" + fileProcessingThreads + ", queueSize=" + writerQueueSize + '}';
    }
}
