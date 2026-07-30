package com.payment.replay;

import com.payment.replay.command.CommandRouter;
import com.payment.replay.command.FilterMaskCommand;
import com.payment.replay.command.ReplayCommand;
import com.payment.replay.config.AppConfig;
import com.payment.replay.config.ConfigLoader;
import com.payment.replay.logging.ErrorReporter;
import com.payment.replay.logging.MetricsCollector;
import com.payment.replay.mapping.BankMappingService;
import com.payment.replay.mapping.QueueNameResolver;
import com.payment.replay.masking.MaskingService;
import com.payment.replay.masking.MaskingStrategyFactory;
import com.payment.replay.mq.MqConnectionManager;
import com.payment.replay.mq.MqPublisher;
import com.payment.replay.parser.LogFileScanner;
import com.payment.replay.parser.LogRecordParser;
import com.payment.replay.parser.SanitizedFileReader;
import com.payment.replay.replay.ReplayOrchestrator;
import com.payment.replay.replay.TimeGrouper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Main entry point for the Payment Replay Tool.
 *
 * Bootstraps the application by:
 * 1. Loading configuration
 * 2. Constructing dependencies (manual DI)
 * 3. Registering commands with the router
 * 4. Dispatching to the appropriate command
 *
 * Usage:
 *   java -jar payment-replay-tool.jar filter-mask <logpath>
 *   java -jar payment-replay-tool.jar replay <logpath>
 */
public final class PaymentReplayApplication {

    private static final Logger log = LoggerFactory.getLogger(PaymentReplayApplication.class);

    public static void main(String[] args) {
        log.info("Payment Replay Tool starting...");

        try {
            // Load configuration
            String configDir = System.getProperty("config.dir");
            ConfigLoader configLoader = new ConfigLoader(configDir);
            AppConfig config = configLoader.loadAll();

            // Build dependency graph (manual constructor injection)
            int exitCode = buildAndRun(config, args);

            log.info("Payment Replay Tool exiting with code: {}", exitCode);
            System.exit(exitCode);

        } catch (Exception e) {
            log.error("Fatal startup error: {}", e.getMessage(), e);
            System.err.println("Fatal error: " + e.getMessage());
            System.exit(2);
        }
    }

    /**
     * Builds the application's dependency graph and executes the command.
     * Separated from main() for testability.
     */
    static int buildAndRun(AppConfig config, String[] args) {
        // Shared services
        MetricsCollector metricsCollector = new MetricsCollector(config);
        ErrorReporter errorReporter = new ErrorReporter(config);

        // Parser components
        LogFileScanner fileScanner = new LogFileScanner();
        LogRecordParser recordParser = new LogRecordParser(config);
        SanitizedFileReader sanitizedFileReader = new SanitizedFileReader();

        // Masking components
        MaskingStrategyFactory strategyFactory = new MaskingStrategyFactory();
        MaskingService maskingService = new MaskingService(config.getMaskFields(), strategyFactory);

        // Mapping components
        BankMappingService bankMappingService = new BankMappingService(config);
        QueueNameResolver queueNameResolver = new QueueNameResolver();

        // MQ components
        MqConnectionManager mqConnectionManager = new MqConnectionManager(config);
        MqPublisher mqPublisher = new MqPublisher(mqConnectionManager);

        // Replay components
        TimeGrouper timeGrouper = new TimeGrouper(config.getReplayConfig());
        ReplayOrchestrator replayOrchestrator = new ReplayOrchestrator(
                config, sanitizedFileReader, timeGrouper, mqPublisher,
                queueNameResolver, metricsCollector, errorReporter);

        // Register commands
        CommandRouter router = new CommandRouter();

        router.register(new FilterMaskCommand(
                config, fileScanner, recordParser, maskingService,
                bankMappingService, metricsCollector, errorReporter));

        router.register(new ReplayCommand(config, replayOrchestrator, metricsCollector, errorReporter));

        // Route to command
        return router.route(args);
    }
}
