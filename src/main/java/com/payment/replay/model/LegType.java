package com.payment.replay.model;

/**
 * Classifies a payment message into a processing leg.
 *
 * LEG1  — Inbound credit transfers and payment amendments.
 *          Message types: pacs.008.*, admn.005.*
 *          Output filename suffix: _leg1
 *          Replay target: Site 1 / Site 2 queues as per switch suffix
 *
 * LEG3  — Inbound payment status reports (return/reject flows).
 *          Message types: pacs.002.*
 *          Output filename suffix: _leg3
 *          Replay target: same queue derivation as Leg1
 */
public enum LegType {

    LEG1("_leg1"),
    LEG3("_leg3");

    private final String fileSuffix;

    LegType(String fileSuffix) {
        this.fileSuffix = fileSuffix;
    }

    /** Suffix appended to the base filename, e.g. raw-20260728100123456789_leg1.log */
    public String getFileSuffix() {
        return fileSuffix;
    }

    /**
     * Determines the LegType from a message type string.
     *
     * @param messageType e.g. "pacs.008.001.08", "admn.005.001.01", "pacs.002.001.10"
     * @return matching LegType, or null if the message type is not processed
     */
    public static LegType from(String messageType) {
        if (messageType == null) return null;
        String lower = messageType.toLowerCase();
        if (lower.startsWith("pacs.008") || lower.startsWith("admn.005")) return LEG1;
        if (lower.startsWith("pacs.002"))                                  return LEG3;
        return null;
    }
}
