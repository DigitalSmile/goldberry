package dev.goldberry.mic.analysis;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
import java.util.ArrayList;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import dev.goldberry.mic.source.Signal;
import dev.goldberry.mic.source.SyntheticSource;

@DisplayName("Levels and LevelTracker")
class LevelsTest {

    @Test
    @DisplayName("a sine's peak is its amplitude and its RMS the amplitude over root two")
    void sine() throws Exception {
        var levels = new ArrayList<Levels>();
        AnalysisFixtures.feed(block -> levels.add(Levels.of(block)), Signal.tone(1_000, 0.5), Duration.ofMillis(10));
        var block = levels.getFirst();
        assertAll(
                () -> assertEquals(0.5f, block.peak(), 1e-4),
                () -> assertEquals(0.5 / Math.sqrt(2), block.rms(), 1e-4),
                () -> assertEquals(-6.02, block.peakDecibels(), 0.01),
                () -> assertEquals(-9.03, block.rmsDecibels(), 0.01));
    }

    @Test
    @DisplayName("silence reads the floor, not minus infinity")
    void silence() {
        assertAll(
                () -> assertEquals(Levels.FLOOR_DECIBELS, Levels.SILENCE.peakDecibels()),
                () -> assertEquals(Levels.FLOOR_DECIBELS, Levels.decibels(1e-12)),
                () -> assertEquals(0, Levels.decibels(1), 1e-12));
    }

    @Test
    @DisplayName("a tracker holds the loudest peak for its hold, then lets it fall at its rate")
    void holdAndFall() throws Exception {
        var tracker = new LevelTracker(Duration.ofMillis(100), 20);
        // One source throughout, so its clock runs on across the change of signal.
        var source = SyntheticSource.of(Signal.tone(1_000, 1));
        source.onSamples(tracker);
        source.advance(Duration.ofMillis(10));
        source.play(Signal.silence());
        source.advance(Duration.ofMillis(100));
        var held = tracker.reading();
        source.advance(Duration.ofMillis(500));
        var falling = tracker.reading();
        assertAll(
                () -> assertEquals(0, held.heldPeakDecibels(), 0.01, "still held at the end of the hold"),
                () -> assertEquals(Levels.FLOOR_DECIBELS, held.levels().peakDecibels(), "the last block is silent"),
                () -> assertEquals(-10, falling.heldPeakDecibels(), 0.3, "half a second at 20 dB/s"),
                () -> assertTrue(falling.heldPeakDecibels() < held.heldPeakDecibels()));
        tracker.reset();
        assertEquals(Levels.FLOOR_DECIBELS, tracker.reading().heldPeakDecibels());
    }
}
