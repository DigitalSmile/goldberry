package io.github.digitalsmile.goldberry.widgets.data;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/// A hole in a series, and the three things a chart may do with it.
///
/// No renderer: this is the arithmetic, and it is the whole of the difference
/// between the three policies. What each one *looks like* is `ChartGapsTest`'s.
class GapsTest {

    private static final Double HOLE = Double.NaN;

    private static List<Double> withHole() {
        return List.of(10.0, 20.0, HOLE, 40.0);
    }

    /// The runs, as `[start, end)` pairs, for readable assertions.
    private static List<List<Integer>> runsOf(Gaps.Resolved resolved) {
        var out = new ArrayList<List<Integer>>();
        for (var run : resolved.runs()) {
            out.add(List.of(run[0], run[1]));
        }
        return out;
    }

    @Test
    @DisplayName("a gap leaves the hole and breaks the series in two")
    void gapIsTheDefault() {
        var resolved = Gaps.resolve(withHole(), NullPolicy.GAP);

        assertEquals(withHole(), resolved.values(), "nothing is substituted");
        assertEquals(List.of(List.of(0, 2), List.of(3, 4)), runsOf(resolved), "two runs, because a hole is a hole");
        assertFalse(resolved.has(2));
        assertFalse(resolved.isWhole());
    }

    @Test
    @DisplayName("null is a hole, and so is a policy nobody chose")
    void theDefaultsAreTheHonestOnes() {
        // A missing value is NaN and a `null` is read as one, so a series built
        // from a nullable column does not have to be converted by its caller --
        // and the obvious conversion is `orElse(0)`, which is the one answer that
        // destroys the difference this enum is about.
        var series = new Series("Downloads", java.util.Arrays.asList(10.0, null, 30.0));
        assertTrue(Double.isNaN(series.values().get(1)), "a null became a hole");
        assertEquals(2, series.valueCount());

        assertEquals(
                Gaps.resolve(withHole(), NullPolicy.GAP).values(),
                Gaps.resolve(withHole(), null).values(),
                "no policy is the pessimistic policy");
    }

    @Test
    @DisplayName("connect interpolates the hole, so the line goes straight across it")
    void connectFillsIn() {
        var resolved = Gaps.resolve(withHole(), NullPolicy.CONNECT);

        // Halfway between 20 and 40, which is the point the straight segment
        // would have passed through anyway -- so a line and a filled band get the
        // same shape from one substitution.
        assertEquals(List.of(10.0, 20.0, 30.0, 40.0), resolved.values());
        assertEquals(List.of(List.of(0, 4)), runsOf(resolved), "one run: no hole left");
        assertTrue(resolved.isWhole());
    }

    @Test
    @DisplayName("connect interpolates over any length of hole, in even steps")
    void connectSpansMoreThanOne() {
        var resolved = Gaps.resolve(List.of(0.0, HOLE, HOLE, HOLE, 40.0), NullPolicy.CONNECT);

        assertEquals(List.of(0.0, 10.0, 20.0, 30.0, 40.0), resolved.values());
    }

    @Test
    @DisplayName("a hole at either end stays a hole, even under connect")
    void theEndsCannotBeConnected() {
        var resolved = Gaps.resolve(List.of(HOLE, HOLE, 20.0, 30.0, HOLE), NullPolicy.CONNECT);

        // Connecting needs two ends: a series that starts late did not have a
        // value before it started, and extending the first reading backwards
        // would be adding data rather than joining it.
        assertFalse(resolved.has(0));
        assertFalse(resolved.has(1));
        assertFalse(resolved.has(4));
        assertEquals(List.of(List.of(2, 4)), runsOf(resolved));
    }

    @Test
    @DisplayName("zero says the value was zero, which is the point of it being opt-in")
    void zeroSubstitutes() {
        var resolved = Gaps.resolve(withHole(), NullPolicy.ZERO);

        assertEquals(List.of(10.0, 20.0, 0.0, 40.0), resolved.values());
        assertEquals(List.of(List.of(0, 4)), runsOf(resolved), "one run: nothing is missing");
    }

    @Test
    @DisplayName("an infinity is a hole too")
    void infinityHasNoPositionOnAnAxis() {
        // Not a missing reading, but there is nowhere on an axis to put one, and a
        // scale that included it would collapse every real point onto a pixel.
        var resolved = Gaps.resolve(List.of(1.0, Double.POSITIVE_INFINITY, 3.0), NullPolicy.GAP);

        assertEquals(List.of(List.of(0, 1), List.of(2, 3)), runsOf(resolved));
    }

    @Test
    @DisplayName("a series with no holes is one run, and says so")
    void theCommonCaseIsOneRun() {
        var resolved = Gaps.resolve(List.of(1.0, 2.0, 3.0), NullPolicy.GAP);

        assertTrue(resolved.isWhole(), "which is the fast path every real chart takes");
        assertEquals(List.of(List.of(0, 3)), runsOf(resolved));
    }

    @Test
    @DisplayName("nothing at all is no runs rather than an empty one")
    void emptyIsEmpty() {
        assertEquals(List.of(), Gaps.resolve(List.of(), NullPolicy.GAP).runs());
        assertEquals(List.of(), Gaps.resolve(null, NullPolicy.ZERO).runs());
        assertEquals(
                List.of(),
                Gaps.resolve(List.of(HOLE, HOLE), NullPolicy.CONNECT).runs(),
                "a series of nothing but holes has nothing to draw");
    }

    @Test
    @DisplayName("a hole in one series is a hole in the whole stack")
    void aStackIsOnlyAsCompleteAsItsComponents() {
        var first = Gaps.resolve(List.of(1.0, 2.0, 3.0, 4.0), NullPolicy.GAP);
        var second = Gaps.resolve(List.of(1.0, HOLE, 3.0, 4.0), NullPolicy.GAP);

        // A band's y is a running total, so an index where one component is
        // missing is an index where the total is unknown. Drawing the bands above
        // it as though the missing one were zero would put them at a height
        // nobody reported.
        var runs = Gaps.stackRuns(List.of(first, second), 4);
        assertEquals(
                List.of(List.of(0, 1), List.of(2, 4)),
                runs.stream().map(run -> List.of(run[0], run[1])).toList());
    }

    @Test
    @DisplayName("a hole is not a value smaller than every other")
    void theAxisIgnoresHoles() {
        var series = Series.of("Downloads", 10, 20, Double.NaN, 40);

        // `Math.min` propagates NaN, so before this a series with one missing
        // reading answered NaN here -- and the axis, finding its domain was not
        // finite, fell back to 0..0 and collapsed the whole chart onto one line.
        assertEquals(10.0, series.min());
        assertEquals(40.0, series.max());
        assertEquals(3, series.valueCount());
    }

    @Test
    @DisplayName("a series of nothing but holes is empty, and a chart says so")
    void holesAreNotData() {
        var nothing = List.of(Series.of("Downloads", Double.NaN, Double.NaN));

        assertFalse(ChartParts.hasData(nothing), "which is what a query returning rows of nulls produces");
    }
}
