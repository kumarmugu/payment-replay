package com.payment.replay.masking;

import com.payment.replay.config.MaskFieldConfig;
import org.junit.Before;
import org.junit.Test;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

public class MaskingServiceTest {

    private MaskingService maskingService;

    @Before
    public void setUp() {
        Map<String, String> keepLast4Params = new HashMap<>();
        keepLast4Params.put("n", "4");
        keepLast4Params.put("maskChar", "*");

        Map<String, String> fullMaskParams = new HashMap<>();
        fullMaskParams.put("maskChar", "X");

        List<MaskFieldConfig> fields = Arrays.asList(
                new MaskFieldConfig("DbtrAcct/Id/Othr/Id", "KEEP_LAST_N", keepLast4Params),
                new MaskFieldConfig("Cdtr/Nm", "FULL_MASK", fullMaskParams)
        );

        MaskingStrategyFactory factory = new MaskingStrategyFactory();
        maskingService = new MaskingService(fields, factory);
    }

    @Test
    public void shouldMaskNestedAccountNumber() {
        String xml = "<Document><DbtrAcct><Id><Othr><Id>1234567890123456</Id></Othr></Id></DbtrAcct></Document>";

        String result = maskingService.maskXml(xml);

        assertThat(result).contains("************3456");
        assertThat(result).doesNotContain("1234567890123456");
    }

    @Test
    public void shouldMaskCreditorName() {
        String xml = "<Payment><Cdtr><Nm>John Smith</Nm></Cdtr></Payment>";

        String result = maskingService.maskXml(xml);

        assertThat(result).contains("XXXXXXXXXX");
        assertThat(result).doesNotContain("John Smith");
    }

    @Test
    public void shouldPreserveNonSensitiveFields() {
        String xml = "<Payment><MsgId>MSG001</MsgId><Cdtr><Nm>John Smith</Nm></Cdtr></Payment>";

        String result = maskingService.maskXml(xml);

        assertThat(result).contains("<MsgId>MSG001</MsgId>");
        assertThat(result).doesNotContain("John Smith");
    }

    @Test
    public void shouldHandleNamespacePrefixes() {
        String xml = "<ns1:Document><ns1:DbtrAcct><ns1:Id><ns1:Othr><ns1:Id>9876543210</ns1:Id></ns1:Othr></ns1:Id></ns1:DbtrAcct></ns1:Document>";

        String result = maskingService.maskXml(xml);

        assertThat(result).contains("******3210");
        assertThat(result).doesNotContain("9876543210");
    }

    @Test
    public void shouldHandleEmptyXml() {
        String result = maskingService.maskXml("");
        assertThat(result).isEqualTo("");
    }

    @Test
    public void shouldHandleNullXml() {
        String result = maskingService.maskXml(null);
        assertThat(result).isNull();
    }

    @Test
    public void shouldHandleXmlWithNoMatchingFields() {
        String xml = "<Payment><Amount>100.00</Amount><Currency>SGD</Currency></Payment>";

        String result = maskingService.maskXml(xml);

        assertThat(result).isEqualTo(xml);
    }
}
