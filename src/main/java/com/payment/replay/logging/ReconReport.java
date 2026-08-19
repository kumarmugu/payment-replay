package com.payment.replay.logging;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Consolidated reconciliation report generated after filter-mask execution.
 *
 * Captures:
 *   1. Number of files processed per switch folder
 *   2. Input vs output record counts broken down by message type
 *   3. Banks encountered in logs that are NOT in bank-list.yaml (missing banks)
 *
 * Thread-safe — counters can be incremented from parallel file-processing threads.
 * Written to ./reports/recon-report-<datetime>.txt at the end of processing.
 */
public final class ReconReport {

    private static final Logger log = LoggerFactory.getLogger(ReconReport.class);
    private static final DateTimeFormatter FILE_TS = DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss");

    // Per-switch file counts: switchFolder -> fileCount
    private final ConcurrentHashMap<String, AtomicLong> filesPerSwitch = new ConcurrentHashMap<>();

    // Input record counts: msgTypePrefix -> count (ALL inbound records before filtering)
    private final ConcurrentHashMap<String, AtomicLong> inputByMsgType = new ConcurrentHashMap<>();

    // Output record counts: msgTypePrefix -> count (records written to output)
    private final ConcurrentHashMap<String, AtomicLong> outputByMsgType = new ConcurrentHashMap<>();

    // Per-switch input totals
    private final ConcurrentHashMap<String, AtomicLong> inputPerSwitch = new ConcurrentHashMap<>();

    // Per-switch output totals
    private final ConcurrentHashMap<String, AtomicLong> outputPerSwitch = new ConcurrentHashMap<>();

    // BICs seen in logs but NOT in bank-list (missing banks)
    private final Set<String> missingBanks = ConcurrentHashMap.newKeySet();

    // Known allowed BICs (for quick reference in report)
    private final Set<String> allowedBics;

    private final String reportDirectory;

    public ReconReport(String reportDirectory, Set<String> allowedBics) {
        this.reportDirectory = reportDirectory;
        this.allowedBics = allowedBics;
    }

    // ─── Increment methods (called from parallel threads) ────────────────────

    public void recordFileProcessed(String switchFolder) {
        filesPerSwitch.computeIfAbsent(switchFolder, k -> new AtomicLong()).incrementAndGet();
    }

    public void recordInputRecord(String switchFolder, String msgType) {
        inputByMsgType.computeIfAbsent(normaliseMsgType(msgType), k -> new AtomicLong()).incrementAndGet();
        inputPerSwitch.computeIfAbsent(switchFolder, k -> new AtomicLong()).incrementAndGet();
    }

    public void recordOutputRecord(String switchFolder, String msgType) {
        outputByMsgType.computeIfAbsent(normaliseMsgType(msgType), k -> new AtomicLong()).incrementAndGet();
        outputPerSwitch.computeIfAbsent(switchFolder, k -> new AtomicLong()).incrementAndGet();
    }

    public void recordMissingBank(String bic) {
        if (bic != null && !bic.isEmpty() && !allowedBics.contains(bic)) {
            missingBanks.add(bic);
        }
    }

    // ─── Report generation ───────────────────────────────────────────────────

    /**
     * Writes the consolidated reconciliation report to a file and prints to console.
     */
    public void generate() {
        String report = buildReport();

        // Print to console
        System.out.println(report);

        // Write to file
        try {
            Path dir = Paths.get(reportDirectory);
            Files.createDirectories(dir);
            String filename = "recon-report-" + LocalDateTime.now().format(FILE_TS) + ".txt";
            Path reportFile = dir.resolve(filename);

            try (BufferedWriter writer = Files.newBufferedWriter(reportFile, StandardCharsets.UTF_8)) {
                writer.write(report);
            }

            log.info("Reconciliation report written to: {}", reportFile);
        } catch (IOException e) {
            log.error("Failed to write recon report: {}", e.getMessage());
        }
    }

    private String buildReport() {
        StringBuilder sb = new StringBuilder();
        String sep = "─".repeat(70);

        sb.append("\n").append(sep).append("\n");
        sb.append("  RECONCILIATION REPORT\n");
        sb.append("  Generated: ").append(LocalDateTime.now().format(
                DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))).append("\n");
        sb.append(sep).append("\n\n");

        // Section 1: Files processed per switch
        sb.append("  1. FILES PROCESSED PER SWITCH\n");
        sb.append("  ").append("─".repeat(40)).append("\n");
        sb.append(String.format("  %-12s %s%n", "Switch", "Files"));
        sb.append("  ").append("─".repeat(40)).append("\n");

        long totalFiles = 0;
        for (String sw : new TreeSet<>(filesPerSwitch.keySet())) {
            long count = filesPerSwitch.get(sw).get();
            sb.append(String.format("  %-12s %d%n", sw, count));
            totalFiles += count;
        }
        sb.append("  ").append("─".repeat(40)).append("\n");
        sb.append(String.format("  %-12s %d%n", "TOTAL", totalFiles));
        sb.append("\n");

        // Section 2: Record counts by message type
        sb.append("  2. RECORD COUNTS BY MESSAGE TYPE\n");
        sb.append("  ").append("─".repeat(55)).append("\n");
        sb.append(String.format("  %-20s %12s %12s %12s%n", "Message Type", "Input", "Output", "Filtered"));
        sb.append("  ").append("─".repeat(55)).append("\n");

        // Merge all msg types from both input and output
        TreeSet<String> allMsgTypes = new TreeSet<>();
        allMsgTypes.addAll(inputByMsgType.keySet());
        allMsgTypes.addAll(outputByMsgType.keySet());

        long totalInput = 0, totalOutput = 0;
        for (String mt : allMsgTypes) {
            long in = inputByMsgType.getOrDefault(mt, new AtomicLong()).get();
            long out = outputByMsgType.getOrDefault(mt, new AtomicLong()).get();
            long filtered = in - out;
            sb.append(String.format("  %-20s %12d %12d %12d%n", mt, in, out, filtered));
            totalInput += in;
            totalOutput += out;
        }
        sb.append("  ").append("─".repeat(55)).append("\n");
        sb.append(String.format("  %-20s %12d %12d %12d%n", "TOTAL", totalInput, totalOutput, totalInput - totalOutput));
        sb.append("\n");

        // Per-switch breakdown
        sb.append("  Per-switch totals:\n");
        sb.append(String.format("  %-12s %12s %12s%n", "Switch", "Input", "Output"));
        for (String sw : new TreeSet<>(inputPerSwitch.keySet())) {
            long in = inputPerSwitch.getOrDefault(sw, new AtomicLong()).get();
            long out = outputPerSwitch.getOrDefault(sw, new AtomicLong()).get();
            sb.append(String.format("  %-12s %12d %12d%n", sw, in, out));
        }
        sb.append("\n");

        // Section 3: Missing banks
        sb.append("  3. MISSING BANKS (in logs but NOT in bank-list.yaml)\n");
        sb.append("  ").append("─".repeat(40)).append("\n");
        if (missingBanks.isEmpty()) {
            sb.append("  None — all encountered BICs are in the bank list.\n");
        } else {
            sb.append(String.format("  %-12s %s%n", "BIC", "Status"));
            sb.append("  ").append("─".repeat(40)).append("\n");
            for (String bic : new TreeSet<>(missingBanks)) {
                sb.append(String.format("  %-12s %s%n", bic, "NOT IN BANK LIST"));
            }
            sb.append("\n  ⚠ Records from these banks were EXCLUDED from output.\n");
            sb.append("  Add them to bank-list.yaml if they should be processed.\n");
        }

        sb.append("\n").append(sep).append("\n");
        sb.append("  END OF RECONCILIATION REPORT\n");
        sb.append(sep).append("\n");

        return sb.toString();
    }

    /**
     * Normalises a message type to its short prefix for grouping.
     * e.g. "pacs.008.001.08" -> "pacs.008"
     *      "admn.005.001.01" -> "admn.005"
     *      "pacs.002.001.10" -> "pacs.002"
     */
    private String normaliseMsgType(String msgType) {
        if (msgType == null || msgType.length() < 8) return msgType;
        // Take first 8 chars: "pacs.008", "admn.005", "pacs.002"
        return msgType.substring(0, 8).toLowerCase();
    }
}
