package com.payment.replay.masking;

import com.payment.replay.config.MaskFieldConfig;
import org.junit.Before;
import org.junit.Test;

import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

public class KeepLastNStrategyTest {

    private KeepLastNStrategy strategy;

    @Before
    public void setUp() {
        strategy = new KeepLastNStrategy();
    }

    @Test
    public void shouldKeepLastFourDigits() {
        MaskFieldConfig config = createConfig(4, "*");

        String result = strategy.mask("1234567890123456", config);

        assertThat(result).isEqualTo("************3456");
    }

    @Test
    public void shouldKeepLastTwoChars() {
        MaskFieldConfig config = createConfig(2, "#");

        String result = strategy.mask("ABCDEF", config);

        assertThat(result).isEqualTo("####EF");
    }

    @Test
    public void shouldReturnOriginalIfShorterThanN() {
        MaskFieldConfig config = createConfig(10, "*");

        String result = strategy.mask("SHORT", config);

        assertThat(result).isEqualTo("SHORT");
    }

    @Test
    public void shouldReturnOriginalIfEqualToN() {
        MaskFieldConfig config = createConfig(4, "*");

        String result = strategy.mask("ABCD", config);

        assertThat(result).isEqualTo("ABCD");
    }

    @Test
    public void shouldHandleNullInput() {
        MaskFieldConfig config = createConfig(4, "*");

        assertThat(strategy.mask(null, config)).isNull();
    }

    @Test
    public void shouldHandleEmptyInput() {
        MaskFieldConfig config = createConfig(4, "*");

        assertThat(strategy.mask("", config)).isEqualTo("");
    }

    @Test
    public void shouldPreserveLength() {
        MaskFieldConfig config = createConfig(4, "*");
        String input = "AccountNumber12345";

        String result = strategy.mask(input, config);

        assertThat(result).hasSize(input.length());
        assertThat(result).endsWith("2345");
    }

    private MaskFieldConfig createConfig(int n, String maskChar) {
        Map<String, String> params = new HashMap<>();
        params.put("n", String.valueOf(n));
        params.put("maskChar", maskChar);
        return new MaskFieldConfig("test/path", "KEEP_LAST_N", params);
    }
}
