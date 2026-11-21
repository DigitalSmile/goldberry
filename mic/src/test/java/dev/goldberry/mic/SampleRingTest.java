package dev.goldberry.mic;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.nio.FloatBuffer;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("SampleRing")
class SampleRingTest {

    private static FloatBuffer frames(float... samples) {
        return FloatBuffer.wrap(samples);
    }

    @Test
    @DisplayName("reads back what was written, whole frames, oldest first, across the wrap")
    void wraps() {
        var ring = new SampleRing(3, 2);
        ring.write(frames(1, 1, 2, 2), 2);
        var first = FloatBuffer.allocate(2);
        assertEquals(1, ring.read(first));
        ring.write(frames(3, 3, 4, 4), 2);
        var rest = FloatBuffer.allocate(7);
        assertAll(
                () -> assertEquals(3, ring.read(rest), "a buffer of seven floats takes three stereo frames"),
                () -> assertArrayEquals(new float[] {2, 2, 3, 3, 4, 4, 0}, rest.array()),
                () -> assertEquals(0, ring.available()),
                () -> assertEquals(0, ring.dropped()));
    }

    @Test
    @DisplayName("drops the oldest frames when full, and counts them, even past a whole capacity at once")
    void drops() {
        var ring = new SampleRing(2, 1);
        ring.write(frames(1, 2), 2);
        ring.write(frames(3), 1);
        ring.write(frames(4, 5, 6, 7), 4);
        var into = FloatBuffer.allocate(4);
        assertAll(
                () -> assertEquals(2, ring.read(into)),
                () -> assertArrayEquals(new float[] {6, 7, 0, 0}, into.array()),
                () -> assertEquals(5, ring.dropped()));
    }
}
