package com.payment.replay.command;

import com.payment.replay.config.AppConfig;
import com.payment.replay.config.FilterMaskConfig;
import com.payment.replay.exception.BankMappingException;
import com.payment.replay.exception.MaskingException;
import com.payment.replay.logging.ErrorReporter;
import com.payment.replay.logging.MetricsCollector;
import com.payment.replay.mapping.BankMappingService;
import com.payment.replay.masking.MaskingService;
import com.payment.replay.model.ErrorEntry;
import com.payment.replay.model.LegType;
import com.payment.replay.model.LogRecord;
import com.payment.replay.model.MaskedRecord;
import com.payment.replay.model.ProcessingMetrics;
import com.payment.replay.parser.LogFileScanner;
import com.payment.replay.parser.LogRecordParser;
import com.payment.replay.parser.SettlementCycleFilter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/**
 * Functionality 1: Log File Extraction and XML Data Masking
 *
 * Processes production log files in PARALLEL (one thread per file), producing
 * TWO output files per source file:
 *
 *   - <filename>_leg1.log  →  pacs.008 + admn.005 (credit transfers & amendments)
 *   - <filename>_leg3.log  →  pacs.002 (payment status reports)
 *
 * Pipeline per record:
 *   Parse → Filter (direction=in, msgType, bankList) → Mask XML → Map BIC → Write
 *
 * Performance design for 250 TPS / full-day logs:
 *   - Concurrent file processing (configurable thread count, default 4)
 *   - Streaming I/O — never loads full files into memory
 *   - Synchronised writer access per output file (ConcurrentHashMap of writers)
 *   - MaskingService and BankMappingService are stateless and thread-safe
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
        FilterMaskConfig fmConfig = config.getFilterMaskConfig();

        log.info("=== Filter-Mask Command Started ===");
        log.info("Input directory: {}", inputDirectory);
        log.info("Output directory: {}", config.getOutputDirectory());
        log.info("Parallel threads: {}", fmConfig.getFileProcessingThreads());
        log.info("Leg1 suffix: {}, Leg3 suffix: {}", fmConfig.getLeg1FileSuffix(), fmConfig.getLeg3FileSuffix());

        try {
            // Load settlement cycle filter from input directory
            SettlementCycleFilter settlementFilter = new SettlementCycleFilter(inputDirectory);

            List<Path> logFiles = fileScanner.scanLogFiles(inputDirectory);
            if (logFiles.isEmpty()) {
                log.warn("No log files found in: {}", inputDirectory);
                System.out.println("No log files found in: " + inputDirectory);
                return 1;
            }

            log.info("Found {} log files to process", logFiles.size());
            createOutputDirectories();

            // Thread-safe writer map: key = full output file path string
            ConcurrentHashMap<String, BufferedWriter> writers = new ConcurrentHashMap<>();

            // Process files in parallel
            int threadCount = Math.min(fmConfig.getFileProcessingThreads(), logFiles.size());
            ExecutorService executor = Executors.newFixedThreadPool(threadCount);

            for (Path logFile : logFiles) {
                executor.submit(() -> {
                    try {
                        processFile(logFile, metrics, writers, fmConfig, settlementFilter);
                        metrics.incrementFilesProcessed();
                    } catch (Exception e) {
                        log.error("Unhandled error processing file {}: {}", logFile, e.getMessage(), e);
                    }
                });
            }

            executor.shutdown();
            executor.awaitTermination(4, TimeUnit.HOURS); // Full day log = long runtime

            // Close all writers
            for (BufferedWriter w : writers.values()) {
                closeQuietly(w);
            }

            metrics.markComplete();
            metricsCollector.reportMetrics(metrics);
            errorReporter.flush();

            log.info("=== Filter-Mask Command Completed ===");
            printSummary(metrics);
            return 0;

        } catch (Exception e) {
            log.error("Fatal error during filter-mask: {}", e.getMessage(), e);
            System.err.println("Error: " + e.getMessage());
            return 2;
        }
    }

    private void processFile(Path logFile, ProcessingMetrics metrics,
                             ConcurrentHashMap<String, BufferedWriter> writers,
                             FilterMaskConfig fmConfig, SettlementCycleFilter settlementFilter) {
        String switchFolder = fileScanner.extractSwitchFolder(logFile);
        String baseFileName = logFile.getFileName().toString();

        String stem = baseFileName.endsWith(".log")
                ? baseFileName.substring(0, baseFileName.length() - 4) : baseFileName;

        long linesScanned = recordParser.parseFile(logFile,
                record -> {
                    // Settlement cycle filter: only include records in the instruction list
                    if (!settlementFilter.isAllowed(record.getInstructionId())) {
                        return; // skip — not in settlement cycle
                    }
                    metrics.incrementRecordsMatched();
                    processRecord(record, metrics, writers, switchFolder, stem, fmConfig);
                },
                error -> {
                    metrics.incrementXmlFailures();
                    errorReporter.reportError(ErrorEntry.of(
                            "line-" + error.getLineNumber(),
                            ErrorEntry.ErrorType.LOG_PARSE_ERROR,
                            error.getMessage(), error.getSourceFile(), error.getLineNumber()));
                }
        );

        for (long i = 0; i < linesScanned; i++) {
            metrics.incrementRecordsScanned();
        }
    }

    private void processRecord(LogRecord record, ProcessingMetrics metrics,
                               ConcurrentHashMap<String, BufferedWriter> writers,
                               String switchFolder, String stem, FilterMaskConfig fmConfig) {
        try {
            // 1. Mask sensitive XML fields
            String maskedXml = maskingService.maskXml(record.getXmlPayload());
            metrics.incrementRecordsMasked();

            // 2. Map production BIC to UAT BIC
            String mappedXml;
            String mappedBic;
            String mappedInstrId = record.getInstructionId();
            String mappedMsgId = record.getMessageId();

            if (bankMappingService.hasMappingFor(record.getBankBic())) {
                mappedBic = bankMappingService.mapToUat(record.getBankBic());
                mappedXml = bankMappingService.replaceInXml(maskedXml, record.getBankBic());
                // Replace BIC embedded within instruction ID and message ID
                mappedInstrId = mappedInstrId.replace(record.getBankBic(), mappedBic);
                mappedMsgId = mappedMsgId.replace(record.getBankBic(), mappedBic);
            } else {
                log.warn("No bank mapping for BIC: {} at {}:{}",
                        record.getBankBic(), record.getSourceFile(), record.getLineNumber());
                metrics.incrementBankMappingFailures();
                errorReporter.reportError(ErrorEntry.of(record.getMessageId(),
                        ErrorEntry.ErrorType.BANK_MAPPING_ERROR,
                        "No UAT mapping for BIC: " + record.getBankBic(),
                        record.getSourceFile(), record.getLineNumber()));
                mappedXml = maskedXml;
                mappedBic = record.getBankBic();
            }

            // Also replace any OTHER known production BICs that appear in instrId/msgId/XML
            for (java.util.Map.Entry<String, String> entry : config.getBicToUatMapping().entrySet()) {
                String prodBic = entry.getKey();
                String uatBic = entry.getValue();
                if (!prodBic.equals(record.getBankBic())) {
                    mappedXml = mappedXml.replace(prodBic, uatBic);
                    mappedInstrId = mappedInstrId.replace(prodBic, uatBic);
                    mappedMsgId = mappedMsgId.replace(prodBic, uatBic);
                }
            }

            // 3. Derive queue name and build masked record
            String derivedQueue = record.deriveQueueName(mappedBic);
            MaskedRecord maskedRecord = MaskedRecord.fromLogRecord(record)
                    .maskedXmlPayload(mappedXml)
                    .mappedBankBic(mappedBic)
                    .instructionId(mappedInstrId)
                    .messageId(mappedMsgId)
                    .derivedQueueName(derivedQueue)
                    .build();

            // 4. Determine leg suffix and write to appropriate file
            LegType leg = record.getLegType();
            String suffix = (leg == LegType.LEG3) ? fmConfig.getLeg3FileSuffix() : fmConfig.getLeg1FileSuffix();
            String outputFileName = stem + suffix + ".log";
            String writerKey = switchFolder + "/" + outputFileName;

            writeRecord(maskedRecord, writers, writerKey, switchFolder, outputFileName);
            metrics.incrementRecordsWritten();

        } catch (MaskingException e) {
            metrics.incrementXmlFailures();
            errorReporter.reportError(ErrorEntry.of(record.getMessageId(),
                    ErrorEntry.ErrorType.MASKING_ERROR, e.getMessage(),
                    record.getSourceFile(), record.getLineNumber()));
        } catch (BankMappingException e) {
            metrics.incrementBankMappingFailures();
            errorReporter.reportError(ErrorEntry.of(record.getMessageId(),
                    ErrorEntry.ErrorType.BANK_MAPPING_ERROR, e.getMessage(),
                    record.getSourceFile(), record.getLineNumber()));
        } catch (Exception e) {
            errorReporter.reportError(ErrorEntry.of(record.getMessageId(),
                    ErrorEntry.ErrorType.UNKNOWN_ERROR, e.getMessage(),
                    record.getSourceFile(), record.getLineNumber()));
        }
    }

    private void writeRecord(MaskedRecord record, ConcurrentHashMap<String, BufferedWriter> writers,
                             String writerKey, String switchFolder, String outputFileName) {
        try {
            BufferedWriter writer = writers.computeIfAbsent(writerKey, k -> {
                Path outputFile = Paths.get(config.getOutputDirectory(), switchFolder, outputFileName);
                try {
                    Files.createDirectories(outputFile.getParent());
                    return Files.newBufferedWriter(outputFile, StandardCharsets.UTF_8,
                            StandardOpenOption.CREATE, StandardOpenOption.APPEND);
                } catch (IOException e) {
                    throw new RuntimeException("Cannot open output file: " + outputFile, e);
                }
            });

            // Synchronise writes to the same file from concurrent threads
            synchronized (writer) {
                writer.write(record.toLogLine());
                writer.newLine();
            }
        } catch (IOException e) {
            log.error("Write failure for {}/{}: {}", switchFolder, outputFileName, e.getMessage());
        }
    }

    private void createOutputDirectories() throws IOException {
        String outputDir = config.getOutputDirectory();
        for (String folder : new String[]{"sw1", "sw2", "sw3", "sw4"}) {
            Files.createDirectories(Paths.get(outputDir, folder));
        }
    }

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

    private void closeQuietly(BufferedWriter w) {
        try { w.flush(); w.close(); } catch (IOException e) { /* ignore */ }
    }

    @Override public String getName()        { return "filter-mask"; }
    @Override public String getDescription() { return "Extract, mask, and sanitize production log files (outputs _leg1 and _leg3 files)"; }
    @Override public String getUsage() {
        return "Usage: java -jar payment-replay-tool.jar filter-mask <logpath>\n\n"
                + "Produces two output files per input:\n"
                + "  <filename>_leg1.log  — pacs.008 + admn.005 records\n"
                + "  <filename>_leg3.log  — pacs.002 records\n";
    }
}
