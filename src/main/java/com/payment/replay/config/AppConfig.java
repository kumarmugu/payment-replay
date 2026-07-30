package com.payment.replay.config;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.HashSet;

/**
 * Central application configuration holder.
 * Aggregates all configuration from YAML files into a single immutable object.
 * Loaded once at startup and shared across all components.
 */
public final class AppConfig {

    private final String outputDirectory;
    private final ReplayConfig replayConfig;
    private final MqSiteConfig mqSite1Config;
    private final MqSiteConfig mqSite2Config;
    private final List<MaskFieldConfig> maskFields;
    private final List<BankMappingConfig> bankMappings;
    private final List<BankListConfig> bankList;
    private final Set<String> allowedBics;
    private final Map<String, String> bicToUatMapping;
    private final String errorReportDirectory;
    private final String errorReportFilenamePattern;
    private final boolean metricsEnabled;
    private final int metricsReportIntervalSeconds;

    private AppConfig(Builder builder) {
        this.outputDirectory = builder.outputDirectory;
        this.replayConfig = builder.replayConfig;
        this.mqSite1Config = builder.mqSite1Config;
        this.mqSite2Config = builder.mqSite2Config;
        this.maskFields = Collections.unmodifiableList(builder.maskFields);
        this.bankMappings = Collections.unmodifiableList(builder.bankMappings);
        this.bankList = Collections.unmodifiableList(builder.bankList);
        this.errorReportDirectory = builder.errorReportDirectory;
        this.errorReportFilenamePattern = builder.errorReportFilenamePattern;
        this.metricsEnabled = builder.metricsEnabled;
        this.metricsReportIntervalSeconds = builder.metricsReportIntervalSeconds;

        // Pre-compute lookup structures for fast access
        Set<String> bics = new HashSet<>();
        for (BankListConfig bank : builder.bankList) {
            bics.add(bank.getBic());
        }
        this.allowedBics = Collections.unmodifiableSet(bics);

        java.util.HashMap<String, String> mapping = new java.util.HashMap<>();
        for (BankMappingConfig bankMapping : builder.bankMappings) {
            mapping.put(bankMapping.getProductionBic(), bankMapping.getUatBic());
        }
        this.bicToUatMapping = Collections.unmodifiableMap(mapping);
    }

    public String getOutputDirectory() {
        return outputDirectory;
    }

    public ReplayConfig getReplayConfig() {
        return replayConfig;
    }

    public MqSiteConfig getMqSite1Config() {
        return mqSite1Config;
    }

    public MqSiteConfig getMqSite2Config() {
        return mqSite2Config;
    }

    public List<MaskFieldConfig> getMaskFields() {
        return maskFields;
    }

    public List<BankMappingConfig> getBankMappings() {
        return bankMappings;
    }

    public List<BankListConfig> getBankList() {
        return bankList;
    }

    /**
     * Returns the set of allowed BIC codes for quick lookup during filtering.
     */
    public Set<String> getAllowedBics() {
        return allowedBics;
    }

    /**
     * Returns pre-computed production BIC to UAT BIC mapping.
     */
    public Map<String, String> getBicToUatMapping() {
        return bicToUatMapping;
    }

    /**
     * Checks if a given BIC is in the allowed bank list.
     */
    public boolean isBankAllowed(String bic) {
        return allowedBics.contains(bic);
    }

    /**
     * Gets the UAT BIC for a given production BIC, or null if not mapped.
     */
    public String getUatBic(String productionBic) {
        return bicToUatMapping.get(productionBic);
    }

    /**
     * Returns MQ site configuration based on site number.
     */
    public MqSiteConfig getMqConfigForSite(int siteNumber) {
        if (siteNumber == 2) {
            return mqSite2Config;
        }
        return mqSite1Config;
    }

    public String getErrorReportDirectory() {
        return errorReportDirectory;
    }

    public String getErrorReportFilenamePattern() {
        return errorReportFilenamePattern;
    }

    public boolean isMetricsEnabled() {
        return metricsEnabled;
    }

    public int getMetricsReportIntervalSeconds() {
        return metricsReportIntervalSeconds;
    }

    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder {
        private String outputDirectory = "./output";
        private ReplayConfig replayConfig;
        private MqSiteConfig mqSite1Config;
        private MqSiteConfig mqSite2Config;
        private List<MaskFieldConfig> maskFields = Collections.emptyList();
        private List<BankMappingConfig> bankMappings = Collections.emptyList();
        private List<BankListConfig> bankList = Collections.emptyList();
        private String errorReportDirectory = "./reports";
        private String errorReportFilenamePattern = "error-report-{date}.csv";
        private boolean metricsEnabled = true;
        private int metricsReportIntervalSeconds = 60;

        private Builder() {
        }

        public Builder outputDirectory(String outputDirectory) {
            this.outputDirectory = outputDirectory;
            return this;
        }

        public Builder replayConfig(ReplayConfig replayConfig) {
            this.replayConfig = replayConfig;
            return this;
        }

        public Builder mqSite1Config(MqSiteConfig mqSite1Config) {
            this.mqSite1Config = mqSite1Config;
            return this;
        }

        public Builder mqSite2Config(MqSiteConfig mqSite2Config) {
            this.mqSite2Config = mqSite2Config;
            return this;
        }

        public Builder maskFields(List<MaskFieldConfig> maskFields) {
            this.maskFields = maskFields;
            return this;
        }

        public Builder bankMappings(List<BankMappingConfig> bankMappings) {
            this.bankMappings = bankMappings;
            return this;
        }

        public Builder bankList(List<BankListConfig> bankList) {
            this.bankList = bankList;
            return this;
        }

        public Builder errorReportDirectory(String errorReportDirectory) {
            this.errorReportDirectory = errorReportDirectory;
            return this;
        }

        public Builder errorReportFilenamePattern(String errorReportFilenamePattern) {
            this.errorReportFilenamePattern = errorReportFilenamePattern;
            return this;
        }

        public Builder metricsEnabled(boolean metricsEnabled) {
            this.metricsEnabled = metricsEnabled;
            return this;
        }

        public Builder metricsReportIntervalSeconds(int metricsReportIntervalSeconds) {
            this.metricsReportIntervalSeconds = metricsReportIntervalSeconds;
            return this;
        }

        public AppConfig build() {
            return new AppConfig(this);
        }
    }
}
