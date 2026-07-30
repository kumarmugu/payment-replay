package com.payment.replay.parser;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Scans input directories for log files following the expected naming convention.
 * 
 * Expected structure:
 *   inputDir/
 *     sw1/
 *       raw-202607286578989897.log
 *     sw2/
 *       raw-202607286578989897.log
 *     sw3/
 *     sw4/
 *
 * File naming pattern: raw-<date><sequence>.log
 */
public final class LogFileScanner {

    private static final Logger log = LoggerFactory.getLogger(LogFileScanner.class);

    private static final String LOG_FILE_PREFIX = "raw-";
    private static final String LOG_FILE_EXTENSION = ".log";
    private static final String[] SWITCH_FOLDERS = {"sw1", "sw2", "sw3", "sw4"};

    /**
     * Scans the input directory for all log files across switch folders.
     * Returns files sorted by name (which includes date) for chronological processing.
     *
     * @param inputDirectory path to the root input directory
     * @return sorted list of log file paths found across all switch folders
     */
    public List<Path> scanLogFiles(String inputDirectory) {
        Path rootDir = Paths.get(inputDirectory);

        if (!Files.exists(rootDir)) {
            log.error("Input directory does not exist: {}", inputDirectory);
            return Collections.emptyList();
        }

        if (!Files.isDirectory(rootDir)) {
            log.error("Input path is not a directory: {}", inputDirectory);
            return Collections.emptyList();
        }

        List<Path> allLogFiles = new ArrayList<>();

        for (String switchFolder : SWITCH_FOLDERS) {
            Path switchDir = rootDir.resolve(switchFolder);
            if (Files.exists(switchDir) && Files.isDirectory(switchDir)) {
                List<Path> filesInSwitch = scanSwitchFolder(switchDir);
                allLogFiles.addAll(filesInSwitch);
                log.info("Found {} log files in {}", filesInSwitch.size(), switchDir);
            } else {
                log.debug("Switch folder not found or not a directory: {}", switchDir);
            }
        }

        // Sort by filename for chronological ordering
        Collections.sort(allLogFiles, (a, b) -> a.getFileName().toString().compareTo(b.getFileName().toString()));

        log.info("Total log files found: {}", allLogFiles.size());
        return allLogFiles;
    }

    /**
     * Scans a single switch folder for log files matching the naming pattern.
     *
     * @param switchDir path to a switch subfolder (e.g., sw1)
     * @return list of matching log file paths
     */
    private List<Path> scanSwitchFolder(Path switchDir) {
        List<Path> logFiles = new ArrayList<>();

        try (DirectoryStream<Path> stream = Files.newDirectoryStream(switchDir, this::isLogFile)) {
            for (Path file : stream) {
                logFiles.add(file);
            }
        } catch (IOException e) {
            log.error("Error scanning directory {}: {}", switchDir, e.getMessage());
        }

        return logFiles;
    }

    /**
     * Determines if a file matches the expected log file naming pattern.
     *
     * @param path file path to check
     * @return true if the file matches the log naming convention
     */
    private boolean isLogFile(Path path) {
        if (!Files.isRegularFile(path)) {
            return false;
        }
        String filename = path.getFileName().toString().toLowerCase();
        return filename.startsWith(LOG_FILE_PREFIX) && filename.endsWith(LOG_FILE_EXTENSION);
    }

    /**
     * Extracts the switch folder name from a file path.
     * Example: /input/sw1/raw-20260728.log returns "sw1"
     *
     * @param filePath path to log file
     * @return switch folder name or empty string if not determinable
     */
    public String extractSwitchFolder(Path filePath) {
        Path parent = filePath.getParent();
        if (parent != null) {
            return parent.getFileName().toString();
        }
        return "";
    }
}
