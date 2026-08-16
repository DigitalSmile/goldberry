package io.github.digitalsmile.goldberry;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.digitalsmile.goldberry.backend.PhysicalSize;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

class PaintThreadsTest {

    /// The property is not set: `-1` is what [PaintThreads#resolve] is handed
    /// when nobody has asked for a number.
    private static final int UNSET = -1;

    @ParameterizedTest
    @CsvSource({
        // processors, expected workers
        "1, 0",
        "2, 0", // one worker is the count that measured slower than none
        "3, 2",
        "4, 3",
        "5, 4",
        "8, 4", // capped: eight workers measured worse than four at every size
        "64, 4",
    })
    @DisplayName("the automatic count leaves the UI thread a core, caps at four, and never picks one")
    void automaticCount(int processors, int expected) {
        assertEquals(expected, PaintThreads.automatic(processors));
    }

    @ParameterizedTest
    @ValueSource(ints = {1, 2, 3, 4, 6, 8, 12, 64})
    @DisplayName("the automatic count is never exactly one, on any machine")
    void neverAsksForASingleWorker(int processors) {
        // The finding this whole policy turns on: a single worker pays for the
        // command queue and gets no parallelism back, so it is worse than
        // painting synchronously. No core count may produce it.
        assertNotEquals(1, PaintThreads.automatic(processors));
    }

    @ParameterizedTest
    @CsvSource({
        // width, height, threaded?
        "240,  120,  false",
        "400,  299,  false", // one row short of the floor
        "400,  300,  true",
        "960,  640,  true",
        "3840, 2160, true",
    })
    @DisplayName("a surface too small to divide is painted synchronously")
    void smallSurfacesArePaintedSynchronously(int width, int height, boolean threaded) {
        var workers = PaintThreads.resolve((long) width * height, UNSET, 8);

        assertEquals(threaded, workers > 0,
                () -> width + "x" + height + " resolved to " + workers + " worker(s)");
    }

    @Test
    @DisplayName("an explicit count wins, small surface or not")
    void explicitCountOverridesEverything() {
        // Someone who sets the property is measuring something. A policy that
        // overruled them on a small surface would make the measurement a lie.
        assertEquals(6, PaintThreads.resolve(240L * 120, 6, 8));
        assertEquals(0, PaintThreads.resolve(3840L * 2160, 0, 8));
        assertEquals(1, PaintThreads.resolve(3840L * 2160, 1, 8));
    }

    @Test
    @DisplayName("a 4K surface does not overflow the pixel count")
    void largeSurfacesDoNotOverflow() {
        // int * int would wrap negative somewhere past 46000 square, and a
        // negative pixel count would read as "too small to thread" -- the exact
        // opposite of what an enormous surface wants.
        var huge = new PhysicalSize(60_000, 40_000);

        assertTrue(PaintThreads.resolve(
                (long) huge.width() * huge.height(), UNSET, 8) > 0);
    }

    @Test
    @DisplayName("forSurface agrees with resolve on this machine")
    void forSurfaceUsesThePolicy() {
        var size = new PhysicalSize(1920, 1080);

        // No property is set in the test JVM, so the two must agree. If one is
        // ever set for a test run, this asserts nothing rather than failing.
        var configured = System.getProperty(PaintThreads.PROPERTY);
        if (configured == null) {
            assertEquals(
                    PaintThreads.resolve(
                            1920L * 1080, UNSET, Runtime.getRuntime().availableProcessors()),
                    PaintThreads.forSurface(size));
        }
    }
}
