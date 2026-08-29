package dev.portent.index;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class McVersionTest {

    @Test
    void ordersAcrossTheNumberingChange() {
        // Minecraft went from 1.21.x to 26.x, and component-wise comparison orders that correctly.
        assertThat(McVersion.parse("1.21.4")).isLessThan(McVersion.parse("26.1"));
        assertThat(McVersion.parse("26.1")).isGreaterThan(McVersion.parse("1.21.4"));
        assertThat(McVersion.parse("26.2")).isGreaterThan(McVersion.parse("26.1"));
        assertThat(McVersion.parse("26.1")).isEqualByComparingTo(McVersion.parse("26.1.0"));
    }

    @Test
    void ignoresMavenQualifiers() {
        assertThat(McVersion.parse("26.1-R0.1-SNAPSHOT")).isEqualByComparingTo(McVersion.parse("26.1"));
        assertThat(McVersion.parse("1.21.4-R0.1-SNAPSHOT").toString()).isEqualTo("1.21.4");
    }

    @Test
    void answersThresholdQuestions() {
        assertThat(McVersion.parse("26.1").isAtLeast(26, 1)).isTrue();
        assertThat(McVersion.parse("26.4").isAtLeast(26, 1)).isTrue();
        assertThat(McVersion.parse("27.0").isAtLeast(26, 1)).isTrue();
        assertThat(McVersion.parse("26.0").isAtLeast(26, 1)).isFalse();
        assertThat(McVersion.parse("1.21.4").isAtLeast(26, 1)).isFalse();
    }

    @Test
    void returnsNullWhenNothingNumericCanBeRead() {
        assertThat(McVersion.parse(null)).isNull();
        assertThat(McVersion.parse("")).isNull();
        assertThat(McVersion.parse("unknown")).isNull();
    }
}
