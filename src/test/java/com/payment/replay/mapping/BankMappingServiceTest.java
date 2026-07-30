package com.payment.replay.mapping;

import com.payment.replay.config.AppConfig;
import com.payment.replay.config.BankMappingConfig;
import com.payment.replay.config.MqSiteConfig;
import com.payment.replay.config.ReplayConfig;
import com.payment.replay.exception.BankMappingException;
import org.junit.Before;
import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

public class BankMappingServiceTest {

    private BankMappingService service;

    @Before
    public void setUp() {
        List<BankMappingConfig> mappings = Arrays.asList(
                new BankMappingConfig("DBSSSGSG", "DBSSSGS0"),
                new BankMappingConfig("OCBCSGSG", "OCBCUTS0")
        );

        AppConfig config = AppConfig.builder()
                .bankMappings(mappings)
                .bankList(Collections.emptyList())
                .maskFields(Collections.emptyList())
                .replayConfig(new ReplayConfig(60, 250, "./output"))
                .mqSite1Config(MqSiteConfig.builder().build())
                .mqSite2Config(MqSiteConfig.builder().build())
                .build();

        service = new BankMappingService(config);
    }

    @Test
    public void shouldMapProductionBicToUat() {
        assertThat(service.mapToUat("DBSSSGSG")).isEqualTo("DBSSSGS0");
        assertThat(service.mapToUat("OCBCSGSG")).isEqualTo("OCBCUTS0");
    }

    @Test
    public void shouldThrowForUnmappedBic() {
        assertThatThrownBy(() -> service.mapToUat("UNKNOWN"))
                .isInstanceOf(BankMappingException.class)
                .hasMessageContaining("No UAT mapping found");
    }

    @Test
    public void shouldThrowForNullBic() {
        assertThatThrownBy(() -> service.mapToUat(null))
                .isInstanceOf(BankMappingException.class);
    }

    @Test
    public void shouldReplaceBicInXml() {
        String xml = "<Document><BICCode>DBSSSGSG</BICCode><InstgAgt><FinInstnId><BIC>DBSSSGSG</BIC></FinInstnId></InstgAgt></Document>";

        String result = service.replaceInXml(xml, "DBSSSGSG");

        assertThat(result).doesNotContain("DBSSSGSG");
        assertThat(result).contains("DBSSSGS0");
        // Should replace ALL occurrences
        assertThat(countOccurrences(result, "DBSSSGS0")).isEqualTo(2);
    }

    @Test
    public void shouldReplaceBicInQueueName() {
        String queueName = "DBSSSGSG_REQUEST.TO.G3_1";

        String result = service.replaceInQueueName(queueName, "DBSSSGSG");

        assertThat(result).isEqualTo("DBSSSGS0_REQUEST.TO.G3_1");
    }

    @Test
    public void shouldCheckMappingExists() {
        assertThat(service.hasMappingFor("DBSSSGSG")).isTrue();
        assertThat(service.hasMappingFor("UNKNOWN")).isFalse();
        assertThat(service.hasMappingFor(null)).isFalse();
    }

    @Test
    public void shouldHandleEmptyXml() {
        assertThat(service.replaceInXml("", "DBSSSGSG")).isEqualTo("");
        assertThat(service.replaceInXml(null, "DBSSSGSG")).isNull();
    }

    private int countOccurrences(String text, String target) {
        int count = 0;
        int idx = 0;
        while ((idx = text.indexOf(target, idx)) != -1) {
            count++;
            idx += target.length();
        }
        return count;
    }
}
