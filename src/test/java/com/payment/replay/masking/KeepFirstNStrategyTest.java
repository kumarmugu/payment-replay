package com.payment.replay.masking;

import com.payment.replay.config.MaskFieldConfig;
import org.junit.Before;
import org.junit.Test;

import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

public class KeepFirstNStrategyTest {

    private KeepFirstNStrategy strategy;

    @Before
    public void setUp() {
        strategy = new KeepFirstNStrategy();
    }

    @Test
    public void shouldKeepFirstFourChars() {
        MaskFieldConfig config = createConfig(4, "*");

        String result = strategy.mask("1234567890", config);

        assertThat(result).isEqualTo("1234******");
    }

    @Test
    public void shouldKeepFirstTwoWithCustomMask() {
        MaskFieldConfig config = createConfig(2, "X");

        String result = strategy.mask("ABCDEF", config);

        assertThat(result).isEqualTo("ABXXXX");
    }

    @Test
    public void shouldReturnOriginalIfShorterThanN() {
        MaskFieldConfig config = createConfig(10, "*");

        String result = strategy.mask("SHORT", config);

        assertThat(result).isEqualTo("SHORT");
    }

    @Test
    public void shouldHandleNullInput() {
        MaskFieldConfig config = createConfig(4, "*");

        assertThat(strategy.mask(null, config)).isNull();
    }

    @Test
    public void shouldPreserveLength() {
        MaskFieldConfig config = createConfig(4, "*");
        String input = "SensitiveData123";

        String result = strategy.mask(input, config);

        assertThat(result).hasSize(input.length());
        assertThat(result).startsWith("Sens");
    }

    private MaskFieldConfig createConfig(int n, String maskChar) {
        Map<String, String> params = new HashMap<>();
        params.put("n", String.valueOf(n));
        params.put("maskChar", maskChar);
        return new MaskFieldConfig("test/path", "KEEP_FIRST_N", params);
    }
}
