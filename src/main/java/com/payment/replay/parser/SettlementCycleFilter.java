package com.payment.replay.parser;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collections;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * Loads and validates settlement cycle instruction IDs.
 *
 * The input directory must contain a file named "settlement-cycle.txt" with one
 * instruction ID per line. Only records whose instruction ID appears in this file
 * will be included in the filtered output.
 *
 * Example instruction ID format:
 *   20260805DBSSSGSGBRT7842306
 *
 * The sender bank BIC is embedded within the instruction ID (e.g. "DBSSSGSG" above).
 * This class also provides BIC extraction from instruction IDs for mapping purposes.
 */
public final class SettlementCycleFilter {

    private static final Logger log = LoggerFactory.getLogger(SettlementCycleFilter.class);

    public static final String SETTLEMENT_FILE_NAME = "settlement-cycle.txt";

    private final Set<String> allowedInstructionIds;
    private final boolean enabled;

    /**
     * Creates a filter by loading instruction IDs from the settlement-cycle.txt
     * file located in the given input directory.
     *
     * @param inputDirectory the directory containing log files and settlement-cycle.txt
     */
    public SettlementCycleFilter(String inputDirectory) {
        Path filePath = Paths.get(inputDirectory, SETTLEMENT_FILE_NAME);
        if (Files.exists(filePath)) {
            this.allowedInstructionIds = loadInstructionIds(filePath);
            this.enabled = !allowedInstructionIds.isEmpty();
            log.info("Settlement cycle filter loaded: {} instruction IDs from {}",
                    allowedInstructionIds.size(), filePath);
        } else {
            this.allowedInstructionIds = Collections.emptySet();
            this.enabled = false;
            log.info("No settlement-cycle.txt found in {}. Settlement cycle filtering DISABLED.", inputDirectory);
        }
    }

    /**
     * Returns true if the given instruction ID is in the settlement cycle list.
     * If no settlement-cycle.txt was found, ALL records pass (filter disabled).
     */
    public boolean isAllowed(String instructionId) {
        if (!enabled) {
            return true; // no file = no filtering
        }
        return instructionId != null && allowedInstructionIds.contains(instructionId.trim());
    }

    /** Returns true if settlement cycle filtering is active. */
    public boolean isEnabled() {
        return enabled;
    }

    /** Returns the number of loaded instruction IDs. */
    public int size() {
        return allowedInstructionIds.size();
    }

    /** Position of BIC start within instruction ID (0-indexed). Format: <8-char date><BIC><suffix> */
    private static final int BIC_START_INDEX = 8;

    /**
     * Extracts the sender BIC from an instruction ID.
     *
     * Format: <YYYYMMDD><BIC><suffix>
     * Example: 20260805DBSSSGSGBRT7842306 → DBSSSGSG (8 chars)
     *          20260805UABORSGSGNRT00001 → UABORSGSG (9 chars)
     *
     * Since BIC length can vary (8 or 9 chars), we match against known BICs
     * starting at position 8.
     *
     * @param instructionId the full instruction ID (fixed format)
     * @param bicMapping    known BIC-to-UAT mapping for matching
     * @return the BIC found at position 8, or null if none matches
     */
    public static String extractBicFromInstructionId(String instructionId, Map<String, String> bicMapping) {
        if (instructionId == null || instructionId.length() <= BIC_START_INDEX || bicMapping == null) {
            return null;
        }
        String afterDate = instructionId.substring(BIC_START_INDEX);
        // Try each known BIC; check if the portion after the date starts with it
        for (String bic : bicMapping.keySet()) {
            if (afterDate.startsWith(bic)) {
                return bic;
            }
        }
        return null;
    }

    /**
     * Replaces the BIC in an instruction ID with the UAT BIC.
     * BIC starts at position 8 after the 8-char date prefix.
     *
     * @param instructionId original instruction ID
     * @param productionBic production BIC at position 8 (variable length 8-9 chars)
     * @param uatBic        UAT replacement BIC
     * @return instruction ID with BIC replaced positionally
     */
    public static String replaceBicInInstructionId(String instructionId, String productionBic, String uatBic) {
        if (instructionId == null || productionBic == null || uatBic == null) {
            return instructionId;
        }
        if (instructionId.length() <= BIC_START_INDEX) {
            return instructionId;
        }
        String date = instructionId.substring(0, BIC_START_INDEX);
        String afterDate = instructionId.substring(BIC_START_INDEX);
        if (afterDate.startsWith(productionBic)) {
            String suffix = afterDate.substring(productionBic.length());
            return date + uatBic + suffix;
        }
        return instructionId;
    }

    /**
     * Loads instruction IDs from a text file (one per line).
     * Blank lines and lines starting with '#' are ignored.
     */
    private Set<String> loadInstructionIds(Path filePath) {
        Set<String> ids = new HashSet<>();
        try (BufferedReader reader = Files.newBufferedReader(filePath, StandardCharsets.UTF_8)) {
            String line;
            while ((line = reader.readLine()) != null) {
                String trimmed = line.trim();
                if (!trimmed.isEmpty() && !trimmed.startsWith("#")) {
                    ids.add(trimmed);
                }
            }
        } catch (IOException e) {
            log.error("Failed to load settlement cycle file {}: {}", filePath, e.getMessage());
        }
        return ids;
    }
}
