package com.payment.replay.command;

import com.payment.replay.config.AppConfig;
import com.payment.replay.exception.BankMappingException;
import com.payment.replay.exception.LogParsingException;
import com.payment.replay.exception.MaskingException;
import com.payment.replay.logging.ErrorReporter;
import com.payment.replay.logging.MetricsCollector;
import com.payment.replay.mapping.BankMappingService;
import com.payment.replay.masking.MaskingService;
import com.payment.replay.model.ErrorEntry;
import com.payment.replay.model.LogRecord;
import com.payment.replay.model.MaskedRecord;
import com.payment.replay.model.ProcessingMetrics;
import com.payment.replay.parser.LogFileScanner;
import com.payment.replay.parser.LogRecordParser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Functionality 1: Log File Extraction and XML Data Masking
 *
 * Reads production log files, extracts matching queue records,
 * masks sensitive XML fields, maps production BICs to UAT equivalents,
 * and writes sanitized output files preserving the original directory structure.
 *
 * Processing pipeline per record:
 * 1. Parse log line -> LogRecord
 * 2. Filter by queue pattern and bank list
 * 3. Mask sensitive XML fields
 * 4. Replace production BIC with UAT BIC in XML and queue name
 * 5. Write sanitized record to output file
 */
public final class FilterMaskCommand implements Command {

    private static final Logger log = LoggerFactory.getLogger(FilterMaskCommand.class);

    private final AppConfig config;
    private final LogFileScanner fileScanner;
    private final LogRecordParser recordParser;
    private final MaskingService maskingService;
    private final BankMappingService bankMappingService;
    private final MetricsCollector metricsCollector;
    private final ErrorReporter errorReporter;

    public FilterMaskCommand(AppConfig config,
                             LogFileScanner fileScanner,
                             LogRecordParser recordParser,
                             MaskingService maskingService,
                             BankMappingService bankMappingService,
                             MetricsCollector metricsCollector,
                             ErrorReporter errorReporter) {
        this.config = config;
        this.fileScanner = fileScanner;
        this.recordParser = recordParser;
        this.maskingService = maskingService;
        this.bankMappingService = bankMappingService;
        this.metricsCollector = metricsCollector;
        this.errorReporter = errorReporter;
    }

    @Override
    public int execute(String[] args) {
        if (args.length < 1 || "--help".equals(args[0])) {
            System.out.println(getUsage());
            return args.length < 1 ? 1 : 0;
        }

        String inputDirectory = args[0];
        ProcessingMetrics metrics = new ProcessingMetrics();

        log.info("=== Filter-Mask Command Started ===");
        log.info("Input directory: {}", inputDirectory);
        log.info("Output directory: {}", config.getOutputDirectory());

        try {
            // Step 1: Scan for log files
            List<Path> logFiles = fileScanner.scanLogFiles(inputDirectory);

            if (logFiles.isEmpty()) {
                log.warn("No log files found in: {}", inputDirectory);
                System.out.println("No log files found in: " + inputDirectory);
                return 1;
            }

            log.info("Found {} log files to process", logFiles.size());

            // Step 2: Ensure output directories exist
            createOutputDirectories();

            // Step 3: Process each file with output writers per switch folder
            Map<String, BufferedWriter> writers = new HashMap<>();

            try {
                for (Path logFile : logFiles) {
                    processFile(logFile, metrics, writers);
                    metrics.incrementFilesProcessed();
                }
            } finally {
                // Close all writers
                for (BufferedWriter writer : writers.values()) {
                    closeQuietly(writer);
                }
            }

            // Step 4: Finalize
            metrics.markComplete();
            metricsCollector.reportMetrics(metrics);
            errorReporter.flush();

            log.info("=== Filter-Mask Command Completed ===");
            log.info("Duration: {}ms", metrics.getDurationMs());
            log.info("Files processed: {}", metrics.getFilesProcessed());
            log.info("Records scanned: {}", metrics.getRecordsScanned());
            log.info("Records matched: {}", metrics.getRecordsMatched());
            log.info("Records masked: {}", metrics.getRecordsMasked());
            log.info("Records written: {}", metrics.getRecordsWritten());

            printSummary(metrics);
            return 0;

        } catch (Exception e) {
            log.error("Fatal error during filter-mask processing: {}", e.getMessage(), e);
            System.err.println("Error: " + e.getMessage());
            return 2;
        }
    }

    /**
     * Processes a single log file, parsing records, masking, mapping, and writing output.
     */
    private void processFile(Path logFile, ProcessingMetrics metrics, Map<String, BufferedWriter> writers) {
        String switchFolder = fileScanner.extractSwitchFolder(logFile);
        String fileName = logFile.getFileName().toString();

        log.debug("Processing file: {} (switch: {})", fileName, switchFolder);

        long linesScanned = recordParser.parseFile(logFile,
                // Success callback - record matched filters
                record -> {
                    metrics.incrementRecordsMatched();
                    processRecord(record, metrics, writers, switchFolder, fileName);
                },
                // Error callback - record failed to parse
                error -> {
                    metrics.incrementXmlFailures();
                    errorReporter.reportError(ErrorEntry.of(
                            "line-" + error.getLineNumber(),
                            ErrorEntry.ErrorType.LOG_PARSE_ERROR,
                            error.getMessage(),
                            error.getSourceFile(),
                            error.getLineNumber()));
                }
        );

        metrics.getRecordsScanned();
        // Add lines scanned (recordParser tracks individual line counts)
        for (long i = 0; i < linesScanned; i++) {
            metrics.incrementRecordsScanned();
        }
    }

    /**
     * Processes a single matched record through the masking and mapping pipeline.
     */
    private void processRecord(LogRecord record, ProcessingMetrics metrics,
                               Map<String, BufferedWriter> writers,
                               String switchFolder, String fileName) {
        try {
            // Step 1: Mask sensitive XML fields
            String maskedXml = maskingService.maskXml(record.getXmlPayload());
            metrics.incrementRecordsMasked();

            // Step 2: Replace production BIC with UAT BIC in XML
            String mappedXml;
            String mappedBic;

            if (bankMappingService.hasMappingFor(record.getBankBic())) {
                mappedXml = bankMappingService.replaceInXml(maskedXml, record.getBankBic());
                mappedBic = bankMappingService.mapToUat(record.getBankBic());
            } else {
                // No mapping available - use original values but log warning
                log.warn("No bank mapping for BIC: {} in record at {}:{}",
                        record.getBankBic(), record.getSourceFile(), record.getLineNumber());
                metrics.incrementBankMappingFailures();
                errorReporter.reportError(ErrorEntry.of(
                        record.getMessageId(),
                        ErrorEntry.ErrorType.BANK_MAPPING_ERROR,
                        "No UAT mapping for BIC: " + record.getBankBic(),
                        record.getSourceFile(),
                        record.getLineNumber()));
                mappedXml = maskedXml;
                mappedBic = record.getBankBic();
            }

            // Step 3: Derive UAT queue name from mapped BIC + site number
            // Pattern: <uatBic>_REQUEST.TO.G3_<siteNo>
            String derivedQueueName = record.deriveQueueName(mappedBic);

            // Step 4: Build masked record
            MaskedRecord maskedRecord = MaskedRecord.fromLogRecord(record)
                    .maskedXmlPayload(mappedXml)
                    .mappedBankBic(mappedBic)
                    .derivedQueueName(derivedQueueName)
                    .build();

            // Step 4: Write to output file
            writeRecord(maskedRecord, writers, switchFolder, fileName);
            metrics.incrementRecordsWritten();

        } catch (MaskingException e) {
            log.error("Masking failed for record at {}:{}: {}",
                    record.getSourceFile(), record.getLineNumber(), e.getMessage());
            metrics.incrementXmlFailures();
            errorReporter.reportError(ErrorEntry.of(
                    record.getMessageId(),
                    ErrorEntry.ErrorType.MASKING_ERROR,
                    e.getMessage(),
                    record.getSourceFile(),
                    record.getLineNumber()));

        } catch (BankMappingException e) {
            log.error("Bank mapping failed for BIC '{}' at {}:{}: {}",
                    e.getProductionBic(), record.getSourceFile(), record.getLineNumber(), e.getMessage());
            metrics.incrementBankMappingFailures();
            errorReporter.reportError(ErrorEntry.of(
                    record.getMessageId(),
                    ErrorEntry.ErrorType.BANK_MAPPING_ERROR,
                    e.getMessage(),
                    record.getSourceFile(),
                    record.getLineNumber()));

        } catch (Exception e) {
            log.error("Unexpected error processing record at {}:{}: {}",
                    record.getSourceFile(), record.getLineNumber(), e.getMessage());
            errorReporter.reportError(ErrorEntry.of(
                    record.getMessageId(),
                    ErrorEntry.ErrorType.UNKNOWN_ERROR,
                    e.getMessage(),
                    record.getSourceFile(),
                    record.getLineNumber()));
        }
    }

    /**
     * Writes a masked record to the appropriate output file.
     * Output preserves the same file name in the same switch subfolder structure.
     */
    private void writeRecord(MaskedRecord record, Map<String, BufferedWriter> writers,
                             String switchFolder, String fileName) {
        String writerKey = switchFolder + "/" + fileName;

        try {
            BufferedWriter writer = writers.computeIfAbsent(writerKey, key -> {
                Path outputFile = Paths.get(config.getOutputDirectory(), switchFolder, fileName);
                try {
                    Files.createDirectories(outputFile.getParent());
                    return Files.newBufferedWriter(outputFile, StandardCharsets.UTF_8,
                            StandardOpenOption.CREATE, StandardOpenOption.APPEND);
                } catch (IOException e) {
                    log.error("Failed to open output file: {}", outputFile, e);
                    throw new RuntimeException("Cannot open output file: " + outputFile, e);
                }
            });

            writer.write(record.toLogLine());
            writer.newLine();

        } catch (IOException e) {
            log.error("Failed to write record to output file: {}/{}", switchFolder, fileName);
            errorReporter.reportError(ErrorEntry.of(
                    record.getMessageId(),
                    ErrorEntry.ErrorType.FILE_IO_ERROR,
                    "Failed to write output: " + e.getMessage(),
                    record.getSourceFile(),
                    record.getLineNumber()));
        }
    }

    /**
     * Creates the output directory structure (sw1, sw2, sw3, sw4 subfolders).
     */
    private void createOutputDirectories() throws IOException {
        String outputDir = config.getOutputDirectory();
        String[] switchFolders = {"sw1", "sw2", "sw3", "sw4"};
        for (String folder : switchFolders) {
            Path dir = Paths.get(outputDir, folder);
            Files.createDirectories(dir);
        }
        log.debug("Output directories created under: {}", outputDir);
    }

    /**
     * Prints a summary to stdout for user visibility.
     */
    private void printSummary(ProcessingMetrics metrics) {
        System.out.println();
        System.out.println("=== Processing Summary ===");
        System.out.printf("  Files processed:       %d%n", metrics.getFilesProcessed());
        System.out.printf("  Records scanned:       %d%n", metrics.getRecordsScanned());
        System.out.printf("  Records matched:       %d%n", metrics.getRecordsMatched());
        System.out.printf("  Records masked:        %d%n", metrics.getRecordsMasked());
        System.out.printf("  Records written:       %d%n", metrics.getRecordsWritten());
        System.out.printf("  Bank mapping failures: %d%n", metrics.getBankMappingFailures());
        System.out.printf("  XML failures:          %d%n", metrics.getXmlFailures());
        System.out.printf("  Duration:              %dms%n", metrics.getDurationMs());
        System.out.printf("  Throughput:            %.1f records/sec%n", metrics.getThroughput());
        System.out.println();
    }

    private void closeQuietly(BufferedWriter writer) {
        if (writer != null) {
            try {
                writer.flush();
                writer.close();
            } catch (IOException e) {
                log.warn("Error closing output writer: {}", e.getMessage());
            }
        }
    }

    @Override
    public String getName() {
        return "filter-mask";
    }

    @Override
    public String getDescription() {
        return "Extract, mask, and sanitize production log files for UAT replay";
    }

    @Override
    public String getUsage() {
        return "Usage: java -jar payment-replay-tool.jar filter-mask <logpath>\n" +
                "\n" +
                "Arguments:\n" +
                "  <logpath>    Input directory containing log files (with sw1-sw4 subfolders)\n" +
                "\n" +
                "Description:\n" +
                "  Reads production log files, extracts matching queue records,\n" +
                "  masks sensitive XML fields, maps production BICs to UAT equivalents,\n" +
                "  and generates sanitized output files.\n" +
                "\n" +
                "Examples:\n" +
                "  java -jar payment-replay-tool.jar filter-mask /data/logs/20260728\n" +
                "  java -jar payment-replay-tool.jar filter-mask ./input";
    }
}
