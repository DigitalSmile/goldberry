package dev.goldberry.mic.analysis;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import dev.goldberry.mic.source.SampleFormat;
import dev.goldberry.mic.source.Signal;

@DisplayName("SpectrumAnalyzer")
class SpectrumAnalyzerTest {

    @Test
    @DisplayName("a full-scale sine on a bin's centre reads 0 dB there, and far below it elsewhere")
    void sineOnABin() throws Exception {
        var analyzer = new SpectrumAnalyzer(SampleFormat.VOICE, 1_024);
        var hertz = 48_000.0 * 64 / 1_024;
        AnalysisFixtures.feed(analyzer, Signal.tone(hertz, 1), Duration.ofMillis(100));
        var spectrum = analyzer.spectrum();
        var bin = spectrum.binOf(hertz);
        assertAll(
                () -> assertEquals(64, bin),
                () -> assertEquals(513, spectrum.bins()),
                () -> assertEquals(hertz, spectrum.hertz(bin), 1e-9),
                () -> assertEquals(0, spectrum.decibels(bin), 0.01),
                () -> assertTrue(spectrum.decibels(200) < -80, "bin 200 reads " + spectrum.decibels(200)));
    }

    @Test
    @DisplayName("hears nothing before it is fed, and does not transform again until fed again")
    void cached() throws Exception {
        var analyzer = new SpectrumAnalyzer(SampleFormat.VOICE, 256);
        assertEquals(Levels.FLOOR_DECIBELS, analyzer.spectrum().decibels(10));
        AnalysisFixtures.feed(analyzer, Signal.tone(1_000, 0.5), Duration.ofMillis(20));
        var first = analyzer.spectrum();
        assertSame(first, analyzer.spectrum());
    }

    @Test
    @DisplayName("bands in log frequency take the loudest bin in each, and put a tone in its own band")
    void bands() throws Exception {
        var analyzer = new SpectrumAnalyzer(SampleFormat.VOICE, 2_048);
        AnalysisFixtures.feed(analyzer, Signal.tone(1_000, 1), Duration.ofMillis(100));
        var bands = analyzer.spectrum().bands(10, 20, 20_000);
        var loudest = 0;
        for (var band = 1; band < bands.length; band++) {
            if (bands[band] > bands[loudest]) {
                loudest = band;
            }
        }
        var expected = loudest;
        assertAll(
                () -> assertEquals(10, bands.length),
                () -> assertEquals(5, expected, "1 kHz is in the sixth of ten bands from 20 Hz to 20 kHz"),
                () -> assertTrue(bands[expected] > -3),
                () -> assertTrue(bands[0] < -60),
                () -> assertThrows(
                        IllegalArgumentException.class,
                        () -> analyzer.spectrum().bands(0, 20, 20_000)));
    }
}
