package io.github.digitalsmile.goldberry;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/// [FrameRing], which is what a `hud` reads.
///
/// Every test here hands it timestamps rather than sleeping, which is the whole
/// reason `record` takes two `long`s instead of calling `nanoTime` itself: a
/// frame rate asserted against a real clock is asserted against the scheduler.
class FrameRingTest {

    private static final long MS = 1_000_000L;

    @Test
    @DisplayName("nothing recorded reports nothing, not zero-as-a-measurement")
    void empty() {
        var ring = new FrameRing();

        assertTrue(ring.isEmpty());
        assertEquals(0, ring.count());
        assertEquals(0, ring.fps());
        assertEquals(0, ring.frameMillis());
        assertEquals(0, ring.paintMillis());
    }

    @Test
    @DisplayName("one frame is a count but not yet a rate")
    void oneFrame() {
        var ring = new FrameRing();
        ring.record(0, 2 * MS);

        assertFalse(ring.isEmpty());
        assertEquals(1, ring.count());
        // A rate is a distance between frames and there is only one of them.
        assertEquals(0, ring.fps());
        assertEquals(0, ring.frameMillis());
        // The paint, though, is a fact about this frame alone.
        assertEquals(2.0, ring.paintMillis(), 1e-9);
    }

    /// **A reading is a range** — [ADR-0154].
    ///
    /// A mean alone hides the shape of the cost, and the shape is usually the
    /// question: two windows both averaging 2 ms are different animals if one
    /// never leaves 1.9–2.1 and the other ranges 0.2–14. The second is a spike
    /// being averaged away over sixty frames, and a `hud` showing only the middle
    /// number cannot say so.
    @Test
    @DisplayName("a stage reports its cheapest frame, its mean and its dearest")
    void spans() {
        var ring = new FrameRing();
        // Three frames whose paint differs, so the mean says one thing and the
        // range says another.
        for (var paint : new long[] {1 * MS, 2 * MS, 9 * MS}) {
            ring.stages(0, 0, 0, 0);
            ring.record(0, paint);
        }

        var paint = ring.paint();
        assertEquals(1.0, paint.min(), 1e-9);
        assertEquals(4.0, paint.mean(), 1e-9, "which is nothing like any frame that happened");
        assertEquals(9.0, paint.max(), 1e-9);
        assertEquals(paint.mean(), ring.paintMillis(), 1e-9,
                "the mean is the same number the scalar accessor always gave");
    }

    /// The stages get the same treatment, and from their own ring rather than
    /// from the paint total.
    @Test
    @DisplayName("each stage keeps its own range")
    void stageSpans() {
        var ring = new FrameRing();
        ring.stages(1 * MS, 4 * MS, 0, 0);
        ring.record(0, 5 * MS);
        ring.stages(3 * MS, 2 * MS, 0, 0);
        ring.record(0, 5 * MS);

        assertEquals(1.0, ring.build().min(), 1e-9);
        assertEquals(3.0, ring.build().max(), 1e-9);
        assertEquals(2.0, ring.style().min(), 1e-9);
        assertEquals(4.0, ring.style().max(), 1e-9);
        assertEquals(3.0, ring.style().mean(), 1e-9);
    }

    /// **Nothing measured is not a range of zeroes.** A source with no window
    /// reports one number three times, which is the honest thing for a mean with
    /// no spread behind it.
    @Test
    @DisplayName("a source that keeps no window reports a flat span")
    void flatSpan() {
        var fixed = FrameStats.of(60, 16.7, 2.5, 100);

        assertEquals(2.5, fixed.paint().min(), 1e-9);
        assertEquals(2.5, fixed.paint().mean(), 1e-9);
        assertEquals(2.5, fixed.paint().max(), 1e-9);
        assertEquals(FrameStats.Span.NONE, new FrameRing().paint());
    }

    @Test
    @DisplayName("a steady 60 Hz loop reads 60 fps")
    void steadyRate() {
        var ring = new FrameRing();
        for (var i = 0; i < 30; i++) {
            var end = i * 16_666_667L;
            ring.record(end - 3 * MS, end);
        }

        assertEquals(60.0, ring.fps(), 0.01);
        assertEquals(16.667, ring.frameMillis(), 0.001);
        assertEquals(3.0, ring.paintMillis(), 1e-9);
        assertEquals(30, ring.count());
    }

    @Test
    @DisplayName("the window forgets: 30 slow frames after 60 fast ones read as the slow ones")
    void windowForgets() {
        var ring = new FrameRing();
        var at = 0L;
        for (var i = 0; i < FrameRing.CAPACITY; i++) {
            at += 16 * MS;
            ring.record(at - MS, at);
        }
        assertEquals(62.5, ring.fps(), 0.01);

        // A full ring's worth of 20 fps, which is what a loop looks like when
        // something has gone wrong in it.
        for (var i = 0; i < FrameRing.CAPACITY; i++) {
            at += 50 * MS;
            ring.record(at - 40 * MS, at);
        }

        assertEquals(20.0, ring.fps(), 0.01);
        assertEquals(50.0, ring.frameMillis(), 0.01);
        assertEquals(40.0, ring.paintMillis(), 0.01);
        // and it still knows how many frames there have been in total, which is
        // the one number the window is not allowed to forget.
        assertEquals(2L * FrameRing.CAPACITY, ring.count());
    }

    @Test
    @DisplayName("an idle gap is inside the window, so the rate falls the moment there is a frame")
    void idleGapCountsAgainstTheRate() {
        var ring = new FrameRing();
        var at = 0L;
        for (var i = 0; i < 10; i++) {
            at += 16 * MS;
            ring.record(at - MS, at);
        }
        var busy = ring.fps();

        // The loop goes quiet for a second, then paints one frame.
        at += 1_000 * MS;
        ring.record(at - MS, at);

        assertTrue(ring.fps() < busy / 5,
                "a second of idle inside a ten-frame window has to dominate the mean,"
                        + " or a HUD would report a rate the window is not achieving");
    }

    @Test
    @DisplayName("a frame that took no time is not a negative one")
    void nonMonotonicPaintIsClamped() {
        var ring = new FrameRing();
        // Two readings of nanoTime around a frame so fast the difference rounds
        // the wrong way is not a thing a mean should be allowed to see.
        ring.record(5 * MS, 4 * MS);

        assertEquals(0, ring.paintMillis());
    }

    @Test
    @DisplayName("capacity is what it says, so a test can fill it")
    void capacity() {
        assertEquals(FrameRing.CAPACITY, new FrameRing().capacity());
    }

    @Test
    @DisplayName("fixed statistics are what a test hands a widget")
    void fixed() {
        var stats = FrameStats.of(59.5, 16.8, 2.25, 400);

        assertEquals(59.5, stats.fps());
        assertEquals(16.8, stats.frameMillis());
        assertEquals(2.25, stats.paintMillis());
        assertEquals(400, stats.count());
        assertFalse(stats.isEmpty());
        assertTrue(FrameStats.none().isEmpty());
    }
}
