package io.github.digitalsmile.goldberry.motion;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import io.github.digitalsmile.goldberry.css.cascade.KeyframeAnimations.Direction;
import io.github.digitalsmile.goldberry.css.cascade.KeyframeAnimations.Entry;
import io.github.digitalsmile.goldberry.css.cascade.KeyframeAnimations.FillMode;

/// CSS's timing model for a keyframe animation: where in an iteration it is,
/// or whether it shows anything at all ([ADR-0353]).
///
/// Linear throughout, and 100 ms iterations, so every expected value is
/// arithmetic a reader can check.
class KeyframeTimingTest {

    private static Entry entry(double delay, double iterations, Direction direction, FillMode fill) {
        return new Entry("k", 100, Easing.LINEAR, delay, iterations, direction, fill);
    }

    @ParameterizedTest(name = "{0} ms into a normal run of 2 → {1}")
    @CsvSource({"0, 0.0", "25, 0.25", "99, 0.99", "100, 0.0", "150, 0.5"})
    @DisplayName("each iteration runs 0 to 1 again")
    void iterations(double elapsed, double expected) {
        assertEquals(expected, KeyframeTrack.progress(entry(0, 2, Direction.NORMAL, FillMode.NONE), elapsed), 1e-9);
    }

    @ParameterizedTest(name = "{0} ms into {1} → {2}")
    @CsvSource({
        "25, REVERSE, 0.75",
        "25, ALTERNATE, 0.25",
        "125, ALTERNATE, 0.75",
        "25, ALTERNATE_REVERSE, 0.75",
        "125, ALTERNATE_REVERSE, 0.25",
    })
    @DisplayName("the direction decides which iterations play backwards")
    void directions(double elapsed, Direction direction, double expected) {
        assertEquals(expected, KeyframeTrack.progress(entry(0, 4, direction, FillMode.NONE), elapsed), 1e-9);
    }

    @Test
    @DisplayName("during the delay nothing shows, unless the fill holds the first frame")
    void delay() {
        assertNull(KeyframeTrack.progress(entry(50, 1, Direction.NORMAL, FillMode.NONE), 20));
        assertEquals(0.0, KeyframeTrack.progress(entry(50, 1, Direction.NORMAL, FillMode.BACKWARDS), 20));
        assertEquals(1.0, KeyframeTrack.progress(entry(50, 1, Direction.REVERSE, FillMode.BOTH), 20));
    }

    @Test
    @DisplayName("after the end nothing shows, unless the fill holds the last frame")
    void end() {
        assertNull(KeyframeTrack.progress(entry(0, 1, Direction.NORMAL, FillMode.NONE), 100));
        assertEquals(1.0, KeyframeTrack.progress(entry(0, 1, Direction.NORMAL, FillMode.FORWARDS), 100));
        assertEquals(
                0.0,
                KeyframeTrack.progress(entry(0, 2, Direction.ALTERNATE, FillMode.FORWARDS), 500),
                "two alternating iterations end where they began");
        assertEquals(
                0.5,
                KeyframeTrack.progress(entry(0, 1.5, Direction.NORMAL, FillMode.BOTH), 500),
                1e-9,
                "one and a half iterations end half way");
    }

    @Test
    @DisplayName("a negative delay starts part way in")
    void negativeDelay() {
        assertEquals(0.3, KeyframeTrack.progress(entry(-30, 1, Direction.NORMAL, FillMode.NONE), 0), 1e-9);
    }

    @Test
    @DisplayName("a loop is running for ever; a finished run holding its last frame is not")
    void running() {
        assertTrue(KeyframeTrack.isRunning(entry(0, Double.POSITIVE_INFINITY, Direction.NORMAL, FillMode.NONE), 1e9));
        assertTrue(KeyframeTrack.isRunning(entry(500, 1, Direction.NORMAL, FillMode.NONE), 10), "waiting is running");
        assertFalse(KeyframeTrack.isRunning(entry(0, 1, Direction.NORMAL, FillMode.BOTH), 100));
        assertFalse(
                KeyframeTrack.isRunning(new Entry("k", 0, Easing.LINEAR, 0, 3, Direction.NORMAL, FillMode.NONE), 0),
                "a zero duration has nothing to run");
    }
}
