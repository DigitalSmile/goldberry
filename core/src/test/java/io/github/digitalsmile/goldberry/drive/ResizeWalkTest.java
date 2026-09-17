package io.github.digitalsmile.goldberry.drive;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import io.github.digitalsmile.goldberry.render.model.LogicalSize;

/// [ResizeWalk] — the size a pixel a frame, there and back.
///
/// Every step is handed the window's own size, so a manager that clamped the
/// last request is walked from its answer; the tests feed the walk what it
/// asked for, and once, what it did not get.
class ResizeWalkTest {

    @Test
    @DisplayName("a step is one pixel on each axis towards the target")
    void onePixelAFrame() {
        var walk = new ResizeWalk(LogicalSize.of(100, 100), LogicalSize.of(110, 105));

        assertEquals(LogicalSize.of(101, 101), walk.next(LogicalSize.of(100, 100)));
        assertEquals(LogicalSize.of(102, 102), walk.next(LogicalSize.of(101, 101)));
    }

    @Test
    @DisplayName("an axis that has arrived waits for the other")
    void anArrivedAxisWaits() {
        var walk = new ResizeWalk(LogicalSize.of(100, 100), LogicalSize.of(103, 101));

        assertEquals(LogicalSize.of(101, 101), walk.next(LogicalSize.of(100, 100)));
        assertEquals(LogicalSize.of(102, 101), walk.next(LogicalSize.of(101, 101)));
        assertEquals(LogicalSize.of(103, 101), walk.next(LogicalSize.of(102, 101)));
    }

    @Test
    @DisplayName("arriving turns the walk round on the same step")
    void turnsRoundAtTheTarget() {
        var walk = new ResizeWalk(LogicalSize.of(100, 100), LogicalSize.of(102, 100));

        assertEquals(LogicalSize.of(101, 100), walk.next(LogicalSize.of(100, 100)));
        assertEquals(LogicalSize.of(102, 100), walk.next(LogicalSize.of(101, 100)));
        assertTrue(walk.isOutward());
        // At the target: there is no step outward, so the walk heads home now.
        assertEquals(LogicalSize.of(101, 100), walk.next(LogicalSize.of(102, 100)));
        assertFalse(walk.isOutward());
        assertEquals(LogicalSize.of(100, 100), walk.next(LogicalSize.of(101, 100)));
        // And back out again.
        assertEquals(LogicalSize.of(101, 100), walk.next(LogicalSize.of(100, 100)));
        assertTrue(walk.isOutward());
    }

    @Test
    @DisplayName("a walk shrinks as readily as it grows")
    void shrinks() {
        var walk = new ResizeWalk(LogicalSize.of(200, 200), LogicalSize.of(198, 190));

        assertEquals(LogicalSize.of(199, 199), walk.next(LogicalSize.of(200, 200)));
    }

    @Test
    @DisplayName("a walk steps from where the window is, not from where it asked to be")
    void stepsFromTheWindowsAnswer() {
        var walk = new ResizeWalk(LogicalSize.of(100, 100), LogicalSize.of(200, 200));
        walk.next(LogicalSize.of(100, 100));

        // The manager clamped the last request to a floor of 150 wide.
        assertEquals(LogicalSize.of(151, 102), walk.next(LogicalSize.of(150, 101)));
    }

    @Test
    @DisplayName("two corners at the same size is nowhere to go")
    void nowhereToGo() {
        var walk = new ResizeWalk(LogicalSize.of(100, 100), LogicalSize.of(100, 100));

        assertEquals(LogicalSize.of(100, 100), walk.next(LogicalSize.of(100, 100)));
    }
}
