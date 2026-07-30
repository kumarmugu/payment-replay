package com.payment.replay.config;

import org.junit.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

public class ConfigLoaderTest {

    @Test
    public void shouldLoadAllConfigFromClasspath() {
        ConfigLoader loader = new ConfigLoader();
        AppConfig config = loader.loadAll();

        assertThat(config).isNotNull();
        assertThat(config.getOutputDirectory()).isEqualTo("./output");
        assertThat(config.getMqSite1Config()).isNotNull();
        assertThat(config.getMqSite2Config()).isNotNull();
        assertThat(config.getReplayConfig()).isNotNull();
    }

    @Test
    public void shouldLoadBankList() {
        ConfigLoader loader = new ConfigLoader();
        List<BankListConfig> bankList = loader.loadBankList();

        assertThat(bankList).isNotEmpty();
        assertThat(bankList.get(0).getBic()).isNotEmpty();
    }

    @Test
    public void shouldLoadMaskFields() {
        ConfigLoader loader = new ConfigLoader();
        List<MaskFieldConfig> fields = loader.loadMaskFields();

        assertThat(fields).isNotEmpty();
        assertThat(fields.get(0).getPath()).isNotEmpty();
        assertThat(fields.get(0).getStrategy()).isNotEmpty();
    }

    @Test
    public void shouldLoadBankMappings() {
        ConfigLoader loader = new ConfigLoader();
        List<BankMappingConfig> mappings = loader.loadBankMappings();

        assertThat(mappings).isNotEmpty();
        assertThat(mappings.get(0).getProductionBic()).isNotEmpty();
        assertThat(mappings.get(0).getUatBic()).isNotEmpty();
    }

    @Test
    public void shouldBuildPreComputedLookups() {
        ConfigLoader loader = new ConfigLoader();
        AppConfig config = loader.loadAll();

        assertThat(config.getAllowedBics()).isNotEmpty();
        assertThat(config.getBicToUatMapping()).isNotEmpty();
    }

    @Test
    public void shouldLoadMqSiteConfig() {
        ConfigLoader loader = new ConfigLoader();
        AppConfig config = loader.loadAll();

        MqSiteConfig site1 = config.getMqSite1Config();
        assertThat(site1.getQueueManager()).isEqualTo("QM_SITE1");
        assertThat(site1.getHost()).isEqualTo("mq-site1.example.com");
        assertThat(site1.getPort()).isEqualTo(1414);
        assertThat(site1.getRetryCount()).isEqualTo(3);
    }

    @Test
    public void shouldLoadReplayConfig() {
        ConfigLoader loader = new ConfigLoader();
        AppConfig config = loader.loadAll();

        ReplayConfig replay = config.getReplayConfig();
        assertThat(replay.getGroupingIntervalSeconds()).isEqualTo(60);
        assertThat(replay.getMaxMessagesPerSecond()).isEqualTo(250);
    }
}
