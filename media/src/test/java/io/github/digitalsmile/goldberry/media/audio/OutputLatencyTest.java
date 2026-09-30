package io.github.digitalsmile.goldberry.media.audio;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assumptions.assumeFalse;

import java.time.Duration;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/// How the output latency providers are asked (ADR-0474).
@DisplayName("OutputLatency")
class OutputLatencyTest {

    private static OutputLatency answering(long millis) {
        return () -> Optional.of(Duration.ofMillis(millis));
    }

    @Test
    @DisplayName("with no providers it is NONE, which answers nothing")
    void none() {
        assertSame(OutputLatency.NONE, OutputLatency.firstOf(List.of()));
        assertEquals(Optional.empty(), OutputLatency.NONE.defaultOutput());
    }

    @Test
    @DisplayName("the first provider with an answer gives it; the ones after are not asked")
    void firstAnswer() {
        var asked = new int[1];
        OutputLatency later = () -> {
            asked[0]++;
            return Optional.of(Duration.ofSeconds(1));
        };
        var latency = OutputLatency.firstOf(List.of(OutputLatency.NONE, answering(120), later));
        assertEquals(Optional.of(Duration.ofMillis(120)), latency.defaultOutput());
        assertEquals(0, asked[0]);
    }

    @Test
    @DisplayName("a provider that throws, answers null or a negative time is passed over")
    void unusableAnswers() {
        OutputLatency throwing = () -> {
            throw new IllegalStateException("the device went away");
        };
        OutputLatency nulls = () -> null;
        var latency = OutputLatency.firstOf(List.of(throwing, nulls, answering(-5), answering(40)));
        assertEquals(Optional.of(Duration.ofMillis(40)), latency.defaultOutput());
        assertEquals(Optional.empty(), OutputLatency.firstOf(List.of(throwing)).defaultOutput());
    }

    @Test
    @DisplayName("a zero latency is an answer, not a silence")
    void zeroIsAnAnswer() {
        var latency = OutputLatency.firstOf(List.of(answering(0), answering(40)));
        assertEquals(Optional.of(Duration.ZERO), latency.defaultOutput());
    }

    @Test
    @DisplayName("off macOS the installed provider answers nothing")
    void installedHere() {
        // CoreAudio's provider is this module's own since ADR-0493, so the scan
        // finds it everywhere; on any other system it has no device to read.
        assumeFalse(System.getProperty("os.name", "").toLowerCase(Locale.ROOT).startsWith("mac"));
        assertEquals(Optional.empty(), OutputLatency.installed().defaultOutput());
    }
}
