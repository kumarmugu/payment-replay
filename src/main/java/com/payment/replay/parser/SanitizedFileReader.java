package com.payment.replay.parser;

import com.payment.replay.model.LegType;
import com.payment.replay.model.ReplayMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.Consumer;

/**
 * Reads sanitized output files (_leg1.log / _leg3.log) for replay.
 *
 * File format (13 comma-separated fields):
 *   [0-10] metadata fields
 *   [11]   masked XML (may contain commas)
 *   [12]   derived queue name
 */
public final class SanitizedFileReader {

    private static final Logger log = LoggerFactory.getLogger(SanitizedFileReader.class);
    private static final int MIN_FIELDS = 13;
    private static final int XML_FIELD_INDEX = 11;
    private static final String[] SWITCH_FOLDERS = {"sw1", "sw2", "sw3", "sw4"};

    /**
     * Reads all sanitized messages for a specific leg type.
     * Scans for files matching the leg's suffix pattern (e.g. *_leg1.log).
     */
    public List<ReplayMessage> readAllMessages(String inputDirectory, LegType legType) {
        List<ReplayMessage> messages = new ArrayList<>();
        readAllMessages(inputDirectory, legType, messages::add);
        return messages;
    }

    /** Backwards-compatible: reads ALL files (both legs). */
    public List<ReplayMessage> readAllMessages(String inputDirectory) {
        List<ReplayMessage> all = new ArrayList<>();
        all.addAll(readAllMessages(inputDirectory, LegType.LEG1));
        all.addAll(readAllMessages(inputDirectory, LegType.LEG3));
        return all;
    }

    public void readAllMessages(String inputDirectory, LegType legType, Consumer<ReplayMessage> consumer) {
        Path rootDir = Paths.get(inputDirectory);
        if (!Files.exists(rootDir)) {
            log.error("Input directory does not exist: {}", inputDirectory);
            return;
        }
        String suffix = legType.getFileSuffix() + ".log"; // e.g. "_leg1.log"
        for (String folder : SWITCH_FOLDERS) {
            Path switchDir = rootDir.resolve(folder);
            if (Files.exists(switchDir) && Files.isDirectory(switchDir)) {
                for (Path file : listFiles(switchDir, suffix)) {
                    readSingleFile(file, consumer);
                }
            }
        }
    }

    public void readSingleFile(Path filePath, Consumer<ReplayMessage> consumer) {
        String src = filePath.toString();
        long lineNo = 0;
        try (BufferedReader reader = Files.newBufferedReader(filePath, StandardCharsets.UTF_8)) {
            String line;
            while ((line = reader.readLine()) != null) {
                lineNo++;
                if (line.trim().isEmpty()) continue;
                ReplayMessage msg = parseLine(line, src, lineNo);
                if (msg != null) consumer.accept(msg);
            }
        } catch (IOException e) {
            log.error("Error reading {}: {}", src, e.getMessage());
        }
    }

    ReplayMessage parseLine(String line, String sourceFile, long lineNumber) {
        if (!line.contains(",mq,")) return null;
        String[] fields = line.split(",", MIN_FIELDS);
        if (fields.length < MIN_FIELDS) return null;

        String timestamp  = fields[0].trim();
        String bankBic    = fields[3].trim();
        String instrId    = fields[7].trim();
        String messageId  = fields[8].trim();
        String xmlPayload = fields[XML_FIELD_INDEX].trim();
        String queueName  = fields[MIN_FIELDS - 1].trim();
        String siteNumber = extractSiteNumber(queueName);

        return ReplayMessage.builder()
                .timestamp(timestamp).bankBic(bankBic)
                .queueName(queueName).siteNumber(siteNumber)
                .xmlPayload(xmlPayload).messageId(messageId)
                .instructionId(instrId).sourceFile(sourceFile)
                .lineNumber(lineNumber).build();
    }

    private String extractSiteNumber(String queueName) {
        if (queueName == null || !queueName.contains("G3_")) return "1";
        return queueName.substring(queueName.lastIndexOf("G3_") + 3);
    }

    private List<Path> listFiles(Path directory, String suffix) {
        List<Path> files = new ArrayList<>();
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(directory, p -> {
            return Files.isRegularFile(p) && p.getFileName().toString().endsWith(suffix);
        })) {
            for (Path f : stream) files.add(f);
        } catch (IOException e) {
            log.error("Error listing files in {}: {}", directory, e.getMessage());
        }
        Collections.sort(files, (a, b) -> a.getFileName().toString().compareTo(b.getFileName().toString()));
        return files;
    }
}
