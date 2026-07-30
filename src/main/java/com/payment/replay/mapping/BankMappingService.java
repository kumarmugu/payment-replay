package com.payment.replay.mapping;

import com.payment.replay.config.AppConfig;
import com.payment.replay.exception.BankMappingException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;

/**
 * Maps production bank BIC codes to UAT equivalents.
 *
 * Responsibilities:
 * 1. Look up production BIC in the configured mapping
 * 2. Replace ALL occurrences of production BIC in XML payload with UAT BIC
 * 3. Replace production BIC in queue name
 *
 * The replacement is case-sensitive and replaces all occurrences throughout the XML
 * to ensure complete sanitization.
 */
public final class BankMappingService {

    private static final Logger log = LoggerFactory.getLogger(BankMappingService.class);

    private final Map<String, String> bicToUatMapping;

    public BankMappingService(AppConfig config) {
        this.bicToUatMapping = config.getBicToUatMapping();
        log.info("Bank mapping service initialized with {} mappings", bicToUatMapping.size());
    }

    /**
     * Maps a production BIC to its UAT equivalent.
     *
     * @param productionBic the production BIC code from the log record
     * @return the corresponding UAT BIC code
     * @throws BankMappingException if no mapping exists for the given BIC
     */
    public String mapToUat(String productionBic) {
        if (productionBic == null || productionBic.isEmpty()) {
            throw new BankMappingException("Production BIC is null or empty", productionBic);
        }

        String uatBic = bicToUatMapping.get(productionBic);
        if (uatBic == null) {
            throw new BankMappingException(
                    "No UAT mapping found for production BIC: " + productionBic, productionBic);
        }

        log.trace("Mapped BIC: {} -> {}", productionBic, uatBic);
        return uatBic;
    }

    /**
     * Replaces all occurrences of the production BIC with the UAT BIC in the XML payload.
     * This ensures that any BIC reference within the XML message is properly sanitized.
     *
     * @param xml           the XML payload to process
     * @param productionBic the production BIC to find
     * @return XML with all production BIC occurrences replaced with UAT BIC
     * @throws BankMappingException if no mapping exists for the given BIC
     */
    public String replaceInXml(String xml, String productionBic) {
        if (xml == null || xml.isEmpty()) {
            return xml;
        }

        String uatBic = mapToUat(productionBic);
        String result = xml.replace(productionBic, uatBic);

        if (log.isTraceEnabled()) {
            int replacements = countOccurrences(xml, productionBic);
            log.trace("Replaced {} occurrence(s) of BIC {} in XML", replacements, productionBic);
        }

        return result;
    }

    /**
     * Replaces the production BIC in the queue name with the UAT equivalent.
     * Queue name pattern: <BANK>_REQUEST.TO.G3_<siteNo>
     *
     * The bank identifier in the queue name may differ from the BIC.
     * This method replaces the production BIC if it appears anywhere in the queue name.
     *
     * @param queueName     the original queue name from production logs
     * @param productionBic the production BIC code
     * @return queue name with UAT BIC substituted where applicable
     * @throws BankMappingException if no mapping exists for the given BIC
     */
    public String replaceInQueueName(String queueName, String productionBic) {
        if (queueName == null || queueName.isEmpty()) {
            return queueName;
        }

        String uatBic = mapToUat(productionBic);

        // Replace BIC in queue name if present
        String result = queueName.replace(productionBic, uatBic);

        log.trace("Queue name mapping: {} -> {}", queueName, result);
        return result;
    }

    /**
     * Checks if a mapping exists for the given production BIC.
     *
     * @param productionBic the BIC to check
     * @return true if a UAT mapping exists
     */
    public boolean hasMappingFor(String productionBic) {
        return productionBic != null && bicToUatMapping.containsKey(productionBic);
    }

    /**
     * Counts occurrences of a substring within a string.
     */
    private int countOccurrences(String text, String target) {
        int count = 0;
        int idx = 0;
        while ((idx = text.indexOf(target, idx)) != -1) {
            count++;
            idx += target.length();
        }
        return count;
    }
}
