package io.github.digitalsmile.goldberry.widgets.data;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/// Where an axis puts its labels.
///
/// The claim is not "it produces some numbers" — it is that the numbers are ones
/// a reader can do arithmetic with, over ranges chosen to be awkward. Every case
/// here is a range that the naive answer (divide and round) gets visibly wrong.
class TicksTest {

    /// Whether `step` is one of the shapes a reader recognises: a preferred
    /// digit times a power of ten.
    private static boolean isNice(double step) {
        var mantissa = step / Math.pow(10, Math.floor(Math.log10(step)));
        for (var nice : List.of(1.0, 2.0, 2.5, 3.0, 4.0, 5.0, 10.0)) {
            if (Math.abs(mantissa - nice) < 1e-6) {
                return true;
            }
        }
        return false;
    }

    @Test
    @DisplayName("labels a round range roundly")
    void roundRanges() {
        var labels = Ticks.extended(0, 100, 5);

        assertEquals(0.0, labels.min());
        assertEquals(100.0, labels.max());
        assertEquals(List.of(0.0, 25.0, 50.0, 75.0, 100.0), labels.values());
    }

    @Test
    @DisplayName("runs past an awkward maximum rather than labelling it")
    void awkwardMaximum() {
        // The case the algorithm exists for. 0..97 at five labels: the naive
        // answer is a step of 24.25, which is a number nobody can place a point
        // against. What comes back is round, and the axis simply runs past the
        // data -- which is what every chart anybody trusts does.
        var labels = Ticks.extended(0, 97, 5);

        assertTrue(isNice(labels.step()), "step " + labels.step() + " is not a readable number");
        assertTrue(labels.max() >= 97, "the axis must reach the data: " + labels.max());
        assertTrue(labels.min() <= 0, "and start at or below it: " + labels.min());
    }

    @Test
    @DisplayName("prefers a round step over an exact label count")
    void nicenessBeatsCount() {
        // Density is weighted twice simplicity, but not enough to buy an ugly
        // step: asking for six labels over 0..10 gets a round step even though
        // 10/5 = 2 would have hit the count exactly.
        var labels = Ticks.extended(0, 10, 6);

        assertTrue(isNice(labels.step()), "step " + labels.step());
        assertTrue(Math.abs(labels.count() - 6) <= 2, "and stays near the count asked for: " + labels.count());
    }

    @Test
    @DisplayName("includes zero when the data spans it")
    void zeroIsALabel() {
        // A reader looks for zero first, and the simplicity term pays a whole
        // point for it -- which is why an axis over -40..60 is labelled through
        // zero rather than from -40 in even steps.
        var labels = Ticks.extended(-40, 60, 5);

        assertTrue(
                labels.values().stream().anyMatch(v -> Math.abs(v) < 1e-9),
                "an axis spanning zero should label it: " + labels.values());
    }

    /// The 2026-09-18 review's W12.
    ///
    /// The simplicity term pays a whole point for a labelling that *contains*
    /// zero, and it decided so with `min % step`. Java's `%` truncates toward
    /// zero, so every negative `min` produced a negative remainder and passed the
    /// `remainder < eps` test — crediting the point to labellings that step
    /// straight over zero. R's `%%`, which this scoring is a port of, is a floor
    /// modulus.
    ///
    /// Scoring only, so it moves a choice rather than breaking one: what it
    /// changes is which candidate wins when two are close, and the guard here is
    /// that a labelling which does *not* contain zero is not preferred over one
    /// that does.
    @Test
    @DisplayName("a labelling that steps over zero is not credited with containing it")
    void zeroIsNotCreditedTwice() {
        // Every candidate this range admits either contains zero or does not, and
        // the one that does should win — which it could not reliably do while its
        // rivals were being paid the same point.
        var labels = Ticks.extended(-7, 23, 4);

        var containsZero = labels.values().stream().anyMatch(v -> Math.abs(v) < 1e-9);
        assertTrue(containsZero, "an axis spanning zero should label it: " + labels.values());

        // And every value is a whole multiple of the step, which is what the
        // floor remainder is really asserting about the axis it scored.
        for (var value : labels.values()) {
            var remainder = value - Math.floor(value / labels.step()) * labels.step();
            assertTrue(
                    remainder < 1e-9 || labels.step() - remainder < 1e-9,
                    () -> value + " is not a multiple of the step " + labels.step());
        }
    }

    @Test
    @DisplayName("handles a range that does not start near zero")
    void offsetRanges() {
        var labels = Ticks.extended(1000, 1004, 5);

        assertTrue(isNice(labels.step()), "step " + labels.step());
        assertTrue(
                labels.min() <= 1000 && labels.max() >= 1004,
                "the axis covers the data: " + labels.min() + "…" + labels.max());
        // And it does not fall back to labelling from zero, which would put the
        // whole series in the last thousandth of the axis.
        assertTrue(labels.min() >= 990, "the axis is about the data: " + labels.min());
    }

    @Test
    @DisplayName("labels a negative range")
    void negativeRanges() {
        var labels = Ticks.extended(-97, -3, 5);

        assertTrue(isNice(labels.step()), "step " + labels.step());
        assertTrue(labels.min() <= -97 && labels.max() >= -3, labels.min() + "…" + labels.max());
    }

    @Test
    @DisplayName("labels very small and very large ranges the same way")
    void scaleInvariance() {
        // The algorithm is scale-free: the labelling of 0..97 nanometres and
        // 0..97 million should differ only by a power of ten. A step that stops
        // being nice at some magnitude is a rounding bug.
        for (var magnitude : List.of(1e-9, 1e-3, 1.0, 1e6, 1e12)) {
            var labels = Ticks.extended(0, 97 * magnitude, 5);
            assertTrue(isNice(labels.step() / magnitude), "at " + magnitude + " the step was " + labels.step());
        }
    }

    @Test
    @DisplayName("a flat series is one label, not an invented spread")
    void flatIsOneLabel() {
        var labels = Ticks.extended(7, 7, 5);

        assertEquals(1, labels.count());
        assertEquals(List.of(7.0), labels.values());
        assertEquals(0.0, labels.step());
    }

    @Test
    @DisplayName("computes each label rather than accumulating the step")
    void labelsDoNotDrift() {
        // 0.1 added to itself ten times is 0.9999999999999999, and that is a
        // number somebody has to read off an axis.
        var labels = Ticks.extended(0, 1, 11);

        for (var value : labels.values()) {
            var rounded = Math.round(value * 1e9) / 1e9;
            assertEquals(rounded, value, 1e-12, "a label drifted: " + labels.values());
        }
    }

    @Test
    @DisplayName("takes the bounds either way round, and refuses what is not a range")
    void argumentsAreChecked() {
        assertEquals(
                Ticks.extended(0, 100, 5).values(), Ticks.extended(100, 0, 5).values());
        assertThrows(IllegalArgumentException.class, () -> Ticks.extended(0, 1, 1));
        assertThrows(IllegalArgumentException.class, () -> Ticks.extended(Double.NaN, 1, 5));
        assertThrows(IllegalArgumentException.class, () -> Ticks.extended(0, Double.POSITIVE_INFINITY, 5));
    }

    /// A clock, so not a test: §1.5's "a cost is guarded by a count, never by a
    /// clock" -- and this has no count to guard it with, because what it watches
    /// is the *work* a search does rather than the answer it gives. Under a
    /// parallel Gradle it measures the machine's load as much as the search.
    ///
    /// So it runs under `./gradlew benchmark` and not under `check`, which is
    /// where a measurement belongs.
    @Test
    @Tag("benchmark")
    @DisplayName("answers quickly enough to run inside a frame")
    void isFastEnoughForAFrame() {
        // It runs per axis per frame, so a millisecond here would be a third of
        // a frame's budget. Loose enough not to be flaky, tight enough to catch
        // a search that stopped pruning.
        var started = System.nanoTime();
        for (var i = 0; i < 1_000; i++) {
            Ticks.extended(0, 97 + i, 5);
        }
        var perCall = (System.nanoTime() - started) / 1_000.0 / 1_000.0;

        assertTrue(perCall < 200, "an axis labelling took " + perCall + " µs, which is too slow to do per frame");
    }
}
