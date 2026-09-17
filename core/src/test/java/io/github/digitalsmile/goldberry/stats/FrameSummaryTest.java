package io.github.digitalsmile.goldberry.stats;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/// [FrameSummary] — the line a run writes at exit, and the ceiling under it.
class FrameSummaryTest {

    @Test
    @DisplayName("the line is fixed-format and locale-proof")
    void describe() {
        var summary = new FrameSummary(300, 2, 1.234, 8.9, 60);

        assertEquals(
                "300 frame(s) painted, 2 late; paint mean 1.23 ms, worst 8.90 ms; display 60.0 Hz", summary.describe());
    }

    @Test
    @DisplayName("a budget is exceeded by more late frames than it allows, and never by no budget")
    void exceeds() {
        var summary = new FrameSummary(300, 5, 1, 1, 0);

        assertFalse(summary.exceeds(5), "five late against a budget of five is on budget");
        assertTrue(summary.exceeds(4));
        assertFalse(summary.exceeds(-1), "a negative budget is no budget");
        assertFalse(FrameSummary.NONE.exceeds(0));
    }

    @Test
    @DisplayName("a negative count is refused")
    void negativeIsRefused() {
        assertThrows(IllegalArgumentException.class, () -> new FrameSummary(-1, 0, 0, 0, 0));
        assertThrows(IllegalArgumentException.class, () -> new FrameSummary(0, -1, 0, 0, 0));
    }

    @Test
    @DisplayName("a source without totals summarizes its window")
    void defaultFromTheWindow() {
        var stats = FrameStats.of(60, 16.7, 2.5, 42);

        var summary = stats.summary();

        assertEquals(42, summary.frames());
        assertEquals(0, summary.late());
        assertEquals(2.5, summary.meanPaintMillis(), 1e-9);
        assertEquals(2.5, summary.worstPaintMillis(), 1e-9);
    }
}
