package com.payment.replay.masking;

import com.payment.replay.config.MaskFieldConfig;
import org.junit.Before;
import org.junit.Test;

import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

public class FullMaskStrategyTest {

    private FullMaskStrategy strategy;

    @Before
    public void setUp() {
        strategy = new FullMaskStrategy();
    }

    @Test
    public void shouldMaskEntireValueWithDefaultChar() {
        MaskFieldConfig config = createConfig("*");

        String result = strategy.mask("John Smith", config);

        assertThat(result).isEqualTo("**********");
    }

    @Test
    public void shouldMaskWithCustomChar() {
        MaskFieldConfig config = createConfig("X");

        String result = strategy.mask("John Smith", config);

        assertThat(result).isEqualTo("XXXXXXXXXX");
    }

    @Test
    public void shouldReturnEmptyForEmptyInput() {
        MaskFieldConfig config = createConfig("*");

        assertThat(strategy.mask("", config)).isEqualTo("");
    }

    @Test
    public void shouldReturnNullForNullInput() {
        MaskFieldConfig config = createConfig("*");

        assertThat(strategy.mask(null, config)).isNull();
    }

    @Test
    public void shouldPreserveLength() {
        MaskFieldConfig config = createConfig("*");
        String input = "1234567890123456";

        String result = strategy.mask(input, config);

        assertThat(result).hasSize(input.length());
        assertThat(result).isEqualTo("****************");
    }

    @Test
    public void shouldReturnCorrectStrategyName() {
        assertThat(strategy.getStrategyName()).isEqualTo("FULL_MASK");
    }

    private MaskFieldConfig createConfig(String maskChar) {
        Map<String, String> params = new HashMap<>();
        params.put("maskChar", maskChar);
        return new MaskFieldConfig("test/path", "FULL_MASK", params);
    }
}
