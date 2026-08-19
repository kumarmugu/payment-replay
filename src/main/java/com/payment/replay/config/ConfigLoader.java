package com.payment.replay.config;

import com.payment.replay.exception.ConfigurationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.yaml.snakeyaml.Yaml;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Loads and parses all YAML configuration files into strongly-typed configuration objects.
 * Supports loading from both classpath resources and external file paths.
 *
 * Configuration resolution order:
 * 1. External file path (if specified via system property or CLI argument)
 * 2. Classpath resource (default)
 */
public final class ConfigLoader {

    private static final Logger log = LoggerFactory.getLogger(ConfigLoader.class);

    private static final String APPLICATION_YAML = "application.yaml";
    private static final String MASK_FIELDS_YAML = "mask-fields.yaml";
    private static final String BANK_MAPPING_YAML = "bank-mapping.yaml";
    private static final String BANK_LIST_YAML = "bank-list.yaml";

    private final String configDirectory;

    /**
     * Creates a ConfigLoader that reads from a specific directory.
     * If configDirectory is null, reads from classpath resources.
     *
     * @param configDirectory external config directory path, or null for classpath
     */
    public ConfigLoader(String configDirectory) {
        this.configDirectory = configDirectory;
    }

    /**
     * Creates a ConfigLoader that reads from classpath resources.
     */
    public ConfigLoader() {
        this(null);
    }

    /**
     * Loads all configuration files and builds the complete AppConfig.
     *
     * @return fully populated AppConfig instance
     * @throws ConfigurationException if any config file is missing or invalid
     */
    public AppConfig loadAll() {
        log.info("Loading configuration files...");

        Map<String, Object> appYaml = loadYaml(APPLICATION_YAML);
        List<MaskFieldConfig> maskFields = loadMaskFields();
        List<BankMappingConfig> bankMappings = loadBankMappings();
        List<BankListConfig> bankList = loadBankList();

        AppConfig config = buildAppConfig(appYaml, maskFields, bankMappings, bankList);

        log.info("Configuration loaded successfully. Banks: {}, Mask fields: {}, Bank mappings: {}",
                bankList.size(), maskFields.size(), bankMappings.size());

        return config;
    }

    /**
     * Loads mask field definitions from mask-fields.yaml.
     */
    @SuppressWarnings("unchecked")
    public List<MaskFieldConfig> loadMaskFields() {
        Map<String, Object> yaml = loadYaml(MASK_FIELDS_YAML);
        List<Map<String, Object>> fields = (List<Map<String, Object>>) yaml.get("fields");

        if (fields == null || fields.isEmpty()) {
            log.warn("No mask fields configured in {}", MASK_FIELDS_YAML);
            return Collections.emptyList();
        }

        List<MaskFieldConfig> result = new ArrayList<>();
        for (Map<String, Object> field : fields) {
            String path = getString(field, "path");
            String strategy = getString(field, "strategy");
            Map<String, String> parameters = toStringMap((Map<String, Object>) field.get("parameters"));

            if (path == null || strategy == null) {
                log.warn("Skipping mask field with missing path or strategy: {}", field);
                continue;
            }

            result.add(new MaskFieldConfig(path, strategy, parameters));
        }

        log.debug("Loaded {} mask field configurations", result.size());
        return result;
    }

    /**
     * Loads bank BIC mapping definitions from bank-mapping.yaml.
     */
    @SuppressWarnings("unchecked")
    public List<BankMappingConfig> loadBankMappings() {
        Map<String, Object> yaml = loadYaml(BANK_MAPPING_YAML);
        List<Map<String, Object>> banks = (List<Map<String, Object>>) yaml.get("banks");

        if (banks == null || banks.isEmpty()) {
            log.warn("No bank mappings configured in {}", BANK_MAPPING_YAML);
            return Collections.emptyList();
        }

        List<BankMappingConfig> result = new ArrayList<>();
        for (Map<String, Object> bank : banks) {
            String productionBic = getString(bank, "productionBic");
            String uatBic = getString(bank, "uatBic");

            if (productionBic == null || uatBic == null) {
                log.warn("Skipping bank mapping with missing BIC: {}", bank);
                continue;
            }

            result.add(new BankMappingConfig(productionBic, uatBic));
        }

        log.debug("Loaded {} bank mapping configurations", result.size());
        return result;
    }

    /**
     * Loads the allowed bank list from bank-list.yaml.
     * Format: simple list of BIC strings under "banks:" key.
     */
    @SuppressWarnings("unchecked")
    public List<BankListConfig> loadBankList() {
        Map<String, Object> yaml = loadYaml(BANK_LIST_YAML);
        List<Object> banks = (List<Object>) yaml.get("banks");

        if (banks == null || banks.isEmpty()) {
            log.warn("No banks configured in {}", BANK_LIST_YAML);
            return Collections.emptyList();
        }

        List<BankListConfig> result = new ArrayList<>();
        for (Object entry : banks) {
            String bic = entry != null ? entry.toString().trim() : null;
            if (bic == null || bic.isEmpty()) {
                log.warn("Skipping empty bank list entry");
                continue;
            }
            result.add(new BankListConfig(bic));
        }

        log.debug("Loaded {} bank list entries", result.size());
        return result;
    }

    /**
     * Builds the complete AppConfig from parsed YAML data.
     */
    @SuppressWarnings("unchecked")
    private AppConfig buildAppConfig(Map<String, Object> appYaml,
                                     List<MaskFieldConfig> maskFields,
                                     List<BankMappingConfig> bankMappings,
                                     List<BankListConfig> bankList) {

        // Output configuration
        Map<String, Object> outputSection = getSection(appYaml, "output");
        String outputDirectory = getString(outputSection, "directory", "./output");

        // Replay configuration
        Map<String, Object> replaySection = getSection(appYaml, "replay");
        ReplayConfig replayConfig = new ReplayConfig(
                getInt(replaySection, "groupingIntervalSeconds", 60),
                getInt(replaySection, "maxMessagesPerSecond", 250),
                getString(replaySection, "inputDirectory", "./output"),
                ReplayConfig.ReplayLegs.from(getString(replaySection, "replayLegs", "both"))
        );

        // Filter-mask configuration
        Map<String, Object> fmSection = getSection(appYaml, "filterMask");
        FilterMaskConfig filterMaskConfig = new FilterMaskConfig(
                getString(fmSection, "leg1FileSuffix", "_leg1"),
                getString(fmSection, "leg3FileSuffix", "_leg3"),
                getInt(fmSection, "fileProcessingThreads", 4),
                getInt(fmSection, "writerQueueSize", 2000)
        );

        // MQ configuration
        Map<String, Object> mqSection = getSection(appYaml, "mq");
        MqSiteConfig site1Config = buildMqSiteConfig(getSection(mqSection, "site1"));
        MqSiteConfig site2Config = buildMqSiteConfig(getSection(mqSection, "site2"));

        // Metrics configuration
        Map<String, Object> metricsSection = getSection(appYaml, "metrics");
        boolean metricsEnabled = getBoolean(metricsSection, "enabled", true);
        int metricsInterval = getInt(metricsSection, "reportIntervalSeconds", 60);

        // Error report configuration
        Map<String, Object> errorReportSection = getSection(appYaml, "errorReport");
        String errorReportDir = getString(errorReportSection, "outputDirectory", "./reports");
        String errorReportPattern = getString(errorReportSection, "filenamePattern", "error-report-{date}.csv");

        return AppConfig.builder()
                .outputDirectory(outputDirectory)
                .replayConfig(replayConfig)
                .filterMaskConfig(filterMaskConfig)
                .mqSite1Config(site1Config)
                .mqSite2Config(site2Config)
                .maskFields(maskFields)
                .bankMappings(bankMappings)
                .bankList(bankList)
                .metricsEnabled(metricsEnabled)
                .metricsReportIntervalSeconds(metricsInterval)
                .errorReportDirectory(errorReportDir)
                .errorReportFilenamePattern(errorReportPattern)
                .build();
    }

    /**
     * Builds MQ site configuration from a YAML section.
     */
    private MqSiteConfig buildMqSiteConfig(Map<String, Object> section) {
        return MqSiteConfig.builder()
                .queueManager(getString(section, "queueManager", ""))
                .host(getString(section, "host", "localhost"))
                .port(getInt(section, "port", 1414))
                .channel(getString(section, "channel", ""))
                .queueNamePrefix(getString(section, "queueNamePrefix", ""))
                .connectionTimeout(getInt(section, "connectionTimeout", 30000))
                .retryCount(getInt(section, "retryCount", 3))
                .retryDelayMs(getLong(section, "retryDelayMs", 5000L))
                .username(getString(section, "username", ""))
                .password(getString(section, "password", ""))
                .build();
    }

    /**
     * Loads and parses a YAML file, first checking external path, then classpath.
     */
    @SuppressWarnings("unchecked")
    private Map<String, Object> loadYaml(String filename) {
        Yaml yaml = new Yaml();

        // Try external config directory first
        if (configDirectory != null) {
            Path externalPath = Paths.get(configDirectory, filename);
            if (Files.exists(externalPath)) {
                log.debug("Loading config from external path: {}", externalPath);
                try (InputStream is = Files.newInputStream(externalPath)) {
                    Map<String, Object> result = yaml.load(is);
                    return result != null ? result : Collections.<String, Object>emptyMap();
                } catch (IOException e) {
                    throw new ConfigurationException(
                            "Failed to load config file: " + externalPath, e);
                }
            }
        }

        // Fall back to classpath
        log.debug("Loading config from classpath: {}", filename);
        try (InputStream is = getClass().getClassLoader().getResourceAsStream(filename)) {
            if (is == null) {
                throw new ConfigurationException(
                        "Configuration file not found on classpath: " + filename);
            }
            Map<String, Object> result = yaml.load(is);
            return result != null ? result : Collections.<String, Object>emptyMap();
        } catch (IOException e) {
            throw new ConfigurationException(
                    "Failed to load config file from classpath: " + filename, e);
        }
    }

    // --- Helper methods for safe YAML map access ---

    @SuppressWarnings("unchecked")
    private Map<String, Object> getSection(Map<String, Object> map, String key) {
        if (map == null || !map.containsKey(key)) {
            return Collections.emptyMap();
        }
        Object value = map.get(key);
        if (value instanceof Map) {
            return (Map<String, Object>) value;
        }
        return Collections.emptyMap();
    }

    private String getString(Map<String, Object> map, String key) {
        return getString(map, key, null);
    }

    private String getString(Map<String, Object> map, String key, String defaultValue) {
        if (map == null || !map.containsKey(key)) {
            return defaultValue;
        }
        Object value = map.get(key);
        return value != null ? value.toString() : defaultValue;
    }

    private int getInt(Map<String, Object> map, String key, int defaultValue) {
        if (map == null || !map.containsKey(key)) {
            return defaultValue;
        }
        Object value = map.get(key);
        if (value instanceof Number) {
            return ((Number) value).intValue();
        }
        try {
            return Integer.parseInt(value.toString());
        } catch (NumberFormatException e) {
            log.warn("Invalid integer value for key '{}': {}, using default: {}", key, value, defaultValue);
            return defaultValue;
        }
    }

    private long getLong(Map<String, Object> map, String key, long defaultValue) {
        if (map == null || !map.containsKey(key)) {
            return defaultValue;
        }
        Object value = map.get(key);
        if (value instanceof Number) {
            return ((Number) value).longValue();
        }
        try {
            return Long.parseLong(value.toString());
        } catch (NumberFormatException e) {
            log.warn("Invalid long value for key '{}': {}, using default: {}", key, value, defaultValue);
            return defaultValue;
        }
    }

    private boolean getBoolean(Map<String, Object> map, String key, boolean defaultValue) {
        if (map == null || !map.containsKey(key)) {
            return defaultValue;
        }
        Object value = map.get(key);
        if (value instanceof Boolean) {
            return (Boolean) value;
        }
        return Boolean.parseBoolean(value.toString());
    }

    private Map<String, String> toStringMap(Map<String, Object> map) {
        if (map == null) {
            return Collections.emptyMap();
        }
        Map<String, String> result = new HashMap<>();
        for (Map.Entry<String, Object> entry : map.entrySet()) {
            result.put(entry.getKey(), entry.getValue() != null ? entry.getValue().toString() : "");
        }
        return result;
    }
}
