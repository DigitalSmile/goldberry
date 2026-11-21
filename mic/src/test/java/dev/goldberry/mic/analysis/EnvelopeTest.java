package dev.goldberry.mic.analysis;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/// The outline of a whole clip, as a voice message's waveform draws it.
@DisplayName("an envelope of a clip")
class EnvelopeTest {

    @Test
    @DisplayName("takes each bucket's lowest and highest frame, oldest first")
    void buckets() {
        var envelope = Envelope.of(new float[] {-0.5f, 0.25f, 0.1f, 0.2f, -1f, 1f}, 1, 3);
        assertAll(
                () -> assertEquals(3, envelope.buckets()),
                () -> assertEquals(-0.5f, envelope.lowest(0)),
                () -> assertEquals(0.25f, envelope.highest(0)),
                () -> assertEquals(0.1f, envelope.lowest(1)),
                () -> assertEquals(0.2f, envelope.highest(1)),
                () -> assertEquals(-1f, envelope.lowest(2)),
                () -> assertEquals(1f, envelope.highest(2)),
                () -> assertEquals(1f, envelope.peak()));
    }

    @Test
    @DisplayName("averages the channels of each frame, as the trackers do")
    void averagesChannels() {
        var envelope = Envelope.of(new float[] {1f, 0f, -1f, -0.5f}, 2, 1);
        assertEquals(-0.75f, envelope.lowest(0));
        assertEquals(0.5f, envelope.highest(0));
    }

    @Test
    @DisplayName("spreads frames that do not divide evenly over the buckets")
    void uneven() {
        var samples = new float[10];
        samples[9] = 0.9f;
        var envelope = Envelope.of(samples, 1, 3);
        assertEquals(3, envelope.buckets());
        assertEquals(0.9f, envelope.highest(2), "the last frame lands in the last bucket");
        assertEquals(0f, envelope.highest(1));
    }

    @Test
    @DisplayName("gives a clip shorter than the buckets one bucket a frame, and nothing none")
    void short_() {
        assertEquals(2, Envelope.of(new float[] {0.1f, 0.2f}, 1, 64).buckets());
        assertEquals(0, Envelope.of(new float[0], 1, 64).buckets());
        assertEquals(0f, Envelope.of(new float[0], 1, 64).peak());
    }

    @Test
    @DisplayName("refuses a channel count or bucket count below one, and half a frame")
    void refuses() {
        assertAll(
                () -> assertThrows(IllegalArgumentException.class, () -> Envelope.of(new float[2], 0, 1)),
                () -> assertThrows(IllegalArgumentException.class, () -> Envelope.of(new float[2], 1, 0)),
                () -> assertThrows(IllegalArgumentException.class, () -> Envelope.of(new float[3], 2, 1)));
    }
}
