package com.payment.replay.mapping;

import org.junit.Before;
import org.junit.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class QueueNameResolverTest {

    private QueueNameResolver resolver;

    @Before
    public void setUp() {
        resolver = new QueueNameResolver();
    }

    @Test
    public void shouldExtractSiteNumber() {
        assertThat(resolver.extractSiteNumber("DBSSSGSG_REQUEST.TO.G3_1")).isEqualTo("1");
        assertThat(resolver.extractSiteNumber("OCBCSGSG_REQUEST.TO.G3_2")).isEqualTo("2");
    }

    @Test
    public void shouldDefaultToSite1ForInvalidQueue() {
        assertThat(resolver.extractSiteNumber("INVALID_QUEUE")).isEqualTo("1");
        assertThat(resolver.extractSiteNumber(null)).isEqualTo("1");
    }

    @Test
    public void shouldExtractBankPrefix() {
        assertThat(resolver.extractBankPrefix("DBSSSGSG_REQUEST.TO.G3_1")).isEqualTo("DBSSSGSG");
        assertThat(resolver.extractBankPrefix("OCBCSGSG_REQUEST.TO.G3_2")).isEqualTo("OCBCSGSG");
    }

    @Test
    public void shouldBuildQueueName() {
        String result = resolver.buildQueueName("DBSSSGS0", "1");
        assertThat(result).isEqualTo("DBSSSGS0_REQUEST.TO.G3_1");
    }

    @Test
    public void shouldResolveTargetSite() {
        assertThat(resolver.resolveTargetSite("DBSSSGSG_REQUEST.TO.G3_1")).isEqualTo(1);
        assertThat(resolver.resolveTargetSite("OCBCSGSG_REQUEST.TO.G3_2")).isEqualTo(2);
    }

    @Test
    public void shouldDefaultToSite1ForUnexpectedSiteNumber() {
        assertThat(resolver.resolveTargetSite("BANK_REQUEST.TO.G3_99")).isEqualTo(1);
    }

    @Test
    public void shouldValidateQueueName() {
        assertThat(resolver.isValidQueueName("DBSSSGSG_REQUEST.TO.G3_1")).isTrue();
        assertThat(resolver.isValidQueueName("INVALID")).isFalse();
        assertThat(resolver.isValidQueueName("")).isFalse();
        assertThat(resolver.isValidQueueName(null)).isFalse();
    }
}
