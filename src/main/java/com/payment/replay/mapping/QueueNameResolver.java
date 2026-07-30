package com.payment.replay.mapping;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Resolves and constructs MQ queue names for message routing.
 *
 * Queue name pattern: <BANK>_REQUEST.TO.G3_<G3_SITE_NO>
 *
 * Responsibilities:
 * - Extract site number from queue name
 * - Validate queue name format
 * - Construct destination queue names for replay
 */
public final class QueueNameResolver {

    private static final Logger log = LoggerFactory.getLogger(QueueNameResolver.class);

    private static final String QUEUE_MIDDLE = "_REQUEST.TO.G3_";
    private static final String G3_PREFIX = "G3_";

    /**
     * Extracts the site number from a queue name.
     * Pattern: <BANK>_REQUEST.TO.G3_<siteNo>
     *
     * @param queueName the full queue name
     * @return site number as string (e.g., "1", "2")
     */
    public String extractSiteNumber(String queueName) {
        if (queueName == null || !queueName.contains(G3_PREFIX)) {
            log.warn("Cannot extract site number from queue name: {}", queueName);
            return "1"; // Default to site 1
        }
        int idx = queueName.lastIndexOf(G3_PREFIX);
        return queueName.substring(idx + G3_PREFIX.length());
    }

    /**
     * Extracts the bank prefix from the queue name.
     * Pattern: <BANK>_REQUEST.TO.G3_<siteNo>
     *
     * @param queueName the full queue name
     * @return bank prefix (e.g., "DBSSSGSG")
     */
    public String extractBankPrefix(String queueName) {
        if (queueName == null || !queueName.contains(QUEUE_MIDDLE)) {
            log.warn("Cannot extract bank prefix from queue name: {}", queueName);
            return "";
        }
        return queueName.substring(0, queueName.indexOf(QUEUE_MIDDLE));
    }

    /**
     * Constructs a queue name from components.
     *
     * @param bankIdentifier the bank BIC or identifier
     * @param siteNumber     the G3 site number
     * @return constructed queue name
     */
    public String buildQueueName(String bankIdentifier, String siteNumber) {
        return bankIdentifier + QUEUE_MIDDLE + siteNumber;
    }

    /**
     * Determines the target MQ site (1 or 2) from the queue name.
     *
     * @param queueName the destination queue name
     * @return 1 or 2 indicating which MQ site to use
     */
    public int resolveTargetSite(String queueName) {
        String siteNumber = extractSiteNumber(queueName);
        try {
            int site = Integer.parseInt(siteNumber.trim());
            if (site == 1 || site == 2) {
                return site;
            }
            log.warn("Unexpected site number '{}' from queue '{}', defaulting to site 1",
                    siteNumber, queueName);
            return 1;
        } catch (NumberFormatException e) {
            log.warn("Non-numeric site number '{}' from queue '{}', defaulting to site 1",
                    siteNumber, queueName);
            return 1;
        }
    }

    /**
     * Validates that a queue name matches the expected pattern.
     *
     * @param queueName the queue name to validate
     * @return true if the queue name is valid
     */
    public boolean isValidQueueName(String queueName) {
        if (queueName == null || queueName.isEmpty()) {
            return false;
        }
        return queueName.contains(QUEUE_MIDDLE) && queueName.contains(G3_PREFIX);
    }
}
