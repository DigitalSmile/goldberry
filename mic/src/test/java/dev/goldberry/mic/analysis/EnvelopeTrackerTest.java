package dev.goldberry.mic.analysis;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.time.Duration;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import dev.goldberry.mic.source.SampleFormat;
import dev.goldberry.mic.source.Signal;

@DisplayName("EnvelopeTracker")
class EnvelopeTrackerTest {

    @Test
    @DisplayName("keeps each bucket's lowest and highest sample, oldest first, silence before")
    void buckets() throws Exception {
        var tracker = new EnvelopeTracker(4, 480);
        AnalysisFixtures.feed(tracker, Signal.tone(1_000, 0.5), Duration.ofMillis(20));
        var envelope = tracker.envelope();
        assertAll(
                () -> assertEquals(4, envelope.buckets()),
                () -> assertEquals(0, envelope.highest(0), "not heard yet"),
                () -> assertEquals(0, envelope.lowest(1)),
                () -> assertEquals(0.5f, envelope.highest(2), 1e-4),
                () -> assertEquals(-0.5f, envelope.lowest(3), 1e-4));
    }

    @Test
    @DisplayName("scrolls: the oldest bucket goes as a new one is finished, and channels are averaged")
    void scrolls() throws Exception {
        var tracker = new EnvelopeTracker(2, 480);
        var stereo = new SampleFormat(48_000, 2);
        AnalysisFixtures.feed(tracker, stereo, Signal.silence(), Duration.ofMillis(20));
        AnalysisFixtures.feed(tracker, stereo, new Signal.Clip(new float[] {0.8f}, true), Duration.ofMillis(10));
        var envelope = tracker.envelope();
        assertAll(
                () -> assertEquals(0, envelope.highest(0)),
                () -> assertEquals(0.8f, envelope.highest(1), 1e-6),
                () -> assertEquals(0.8f, envelope.lowest(1), 1e-6));
        assertThrows(IllegalArgumentException.class, () -> new EnvelopeTracker(0, 1));
    }
}
