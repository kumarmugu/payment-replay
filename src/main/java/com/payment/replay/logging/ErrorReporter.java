package com.payment.replay.logging;

import com.payment.replay.config.AppConfig;
import com.payment.replay.model.ErrorEntry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

/**
 * Generates CSV error reports for failed records during processing.
 *
 * Error reports are written to a configurable directory with filenames
 * following the pattern: error-report-yyyyMMdd.csv
 *
 * Errors are buffered in memory and flushed periodically or on demand.
 * This avoids excessive I/O for high-error-rate scenarios.
 *
 * Thread-safe for concurrent error reporting from multiple processing threads.
 */
public final class ErrorReporter {

    private static final Logger log = LoggerFactory.getLogger(ErrorReporter.class);
    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("yyyyMMdd");
    private static final int BUFFER_SIZE = 100;

    private final String outputDirectory;
    private final String filenamePattern;
    private final List<ErrorEntry> buffer = new ArrayList<>();
    private final Object bufferLock = new Object();

    private BufferedWriter writer;
    private boolean headerWritten = false;

    public ErrorReporter(AppConfig config) {
        this.outputDirectory = config.getErrorReportDirectory();
        this.filenamePattern = config.getErrorReportFilenamePattern();
    }

    /**
     * Reports an error entry. Thread-safe.
     * Errors are buffered and flushed when the buffer is full or flush() is called.
     *
     * @param error the error entry to record
     */
    public void reportError(ErrorEntry error) {
        synchronized (bufferLock) {
            buffer.add(error);

            if (buffer.size() >= BUFFER_SIZE) {
                flushBuffer();
            }
        }
    }

    /**
     * Flushes all buffered errors to the CSV file.
     * Should be called at the end of processing to ensure all errors are written.
     */
    public void flush() {
        synchronized (bufferLock) {
            flushBuffer();
            closeWriter();
        }
    }

    /**
     * Returns the total number of errors reported (buffered + written).
     */
    public int getErrorCount() {
        synchronized (bufferLock) {
            return buffer.size();
        }
    }

    /**
     * Writes buffered errors to the CSV file.
     * Creates the file and writes the header if this is the first write.
     */
    private void flushBuffer() {
        if (buffer.isEmpty()) {
            return;
        }

        try {
            ensureWriterReady();

            for (ErrorEntry entry : buffer) {
                writer.write(entry.toCsvLine());
                writer.newLine();
            }

            writer.flush();
            log.debug("Flushed {} error entries to report", buffer.size());

        } catch (IOException e) {
            log.error("Failed to write error report: {}", e.getMessage());
        }

        buffer.clear();
    }

    /**
     * Ensures the CSV writer is initialized and header is written.
     */
    private void ensureWriterReady() throws IOException {
        if (writer == null) {
            Path reportFile = getReportFilePath();
            Files.createDirectories(reportFile.getParent());

            boolean fileExists = Files.exists(reportFile);
            writer = Files.newBufferedWriter(reportFile, StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE, StandardOpenOption.APPEND);

            if (!fileExists || !headerWritten) {
                writer.write(ErrorEntry.csvHeader());
                writer.newLine();
                headerWritten = true;
            }

            log.info("Error report file: {}", reportFile);
        }
    }

    /**
     * Constructs the report file path using the configured pattern and current date.
     */
    private Path getReportFilePath() {
        String dateStr = LocalDate.now().format(DATE_FORMAT);
        String filename = filenamePattern.replace("{date}", dateStr);
        return Paths.get(outputDirectory, filename);
    }

    /**
     * Closes the CSV writer cleanly.
     */
    private void closeWriter() {
        if (writer != null) {
            try {
                writer.flush();
                writer.close();
            } catch (IOException e) {
                log.warn("Error closing error report writer: {}", e.getMessage());
            }
            writer = null;
        }
    }
}
