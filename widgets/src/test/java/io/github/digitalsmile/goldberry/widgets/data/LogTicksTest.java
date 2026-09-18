package io.github.digitalsmile.goldberry.widgets.data;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/// Where the labels go on a logarithmic axis, and where a log scale puts a value.
///
/// Both halves are here because they have to agree: a gridline drawn at 100 and a
/// point drawn at 100 landing on different pixels is the failure this pairing
/// prevents, and it is the one nobody sees until a screenshot.
class LogTicksTest {

    private static List<String> labels(double min, double max, int target) {
        var ticks = LogTicks.of(min, max, target);
        return ticks.values().stream().map(ticks::label).toList();
    }

    @Test
    @DisplayName("a range of decades is labelled in decades")
    void theObviousCase() {
        assertEquals(List.of("1", "10", "100", "1000"), labels(1, 1000, 5));
    }

    @Test
    @DisplayName("too many decades are strided rather than crowded")
    void aColumnOfTwelveNumbersIsNotAnAxis() {
        var labels = labels(1, 1e12, 5);

        // `1, 10, 100, …, 10^12` is thirteen labels in the space of five.
        assertTrue(labels.size() <= 7, "expected about five labels, got " + labels);
        assertEquals("1", labels.getFirst());
        assertTrue(labels.contains("1000000"), labels.toString());
    }

    @Test
    @DisplayName("too few decades are subdivided by 1-2-5, not by every integer")
    void aShortRangeIsStillAnAxis() {
        // `3…40` is one and a bit decades. Labelling it `1, 10, 100` leaves two
        // labels on the chart; labelling it 1..10 by integers crowds the bottom
        // of the decade, where a log axis has least room.
        var labels = labels(3, 40, 5);

        assertTrue(labels.size() >= 3, "expected a usable number of labels, got " + labels);
        for (var label : labels) {
            var mantissa = Double.parseDouble(label) / Math.pow(10, Math.floor(Math.log10(Double.parseDouble(label))));
            assertTrue(
                    Math.abs(mantissa - 1) < 1e-9 || Math.abs(mantissa - 2) < 1e-9 || Math.abs(mantissa - 5) < 1e-9,
                    label + " is not a 1-2-5 subdivision");
        }
    }

    @Test
    @DisplayName("a range with one decade in it is subdivided at every target, not only at five")
    void theBoundingDecadesAreNotTheDecadesOnTheAxis() {
        // `3…40` touches three powers of ten -- 1, 10 and 100 -- and contains
        // exactly one. Counting the bounds made it three decades, which is
        // "enough to stand on their own" at a target of three, so the axis was
        // strided and came out as the single label `10`. The target five was the
        // one this file happened to ask for, and the one it does not show at.
        assertTrue(labels(3, 40, 3).size() >= 3, "expected three labels, got " + labels(3, 40, 3));
        for (var target = 2; target <= 8; target++) {
            assertTrue(
                    labels(3, 40, target).size() >= 2,
                    "at a target of " + target + ", 3…40 was labelled " + labels(3, 40, target));
        }
    }

    @Test
    @DisplayName("four decades asked for five labels get four, not two")
    void aStrideIsForTooManyDecadesRatherThanTooFew() {
        // `3…30000` holds 10, 100, 1000 and 10 000. Counting 1 and 100 000 as
        // well made it six decades against a target of five, so it strode over
        // every other one and drew `100, 10000` -- two labels on a chart that
        // asked for five, which is what `ChartLogTest`'s golden showed.
        assertEquals(List.of("10", "100", "1000", "10000"), labels(3, 30000, 5));
    }

    @Test
    @DisplayName("every label is inside the range it labels")
    void nothingOutsideTheAxis() {
        for (var ticks : List.of(
                LogTicks.of(3, 40, 5), LogTicks.of(1, 1000, 4),
                LogTicks.of(0.004, 7, 5), LogTicks.of(120, 880, 5))) {
            for (var value : ticks.values()) {
                assertTrue(value > 0, value + " is not positive");
            }
        }
        for (var value : LogTicks.of(3, 40, 5).values()) {
            assertTrue(value >= 3 * 0.999999 && value <= 40 * 1.000001, value + " is outside 3…40");
        }
    }

    @Test
    @DisplayName("the decimals come from the value's own magnitude")
    void aLogAxisHasNoStepToRoundTo() {
        // A linear axis takes its decimals from the step, because every label is
        // the same distance from the next. On a log axis they are not, so `0.01`
        // needs two decimals and `1000` needs none -- and giving them all the
        // same number reads `0.01, 0.10, 1.00, 10.00`.
        var labels = labels(0.01, 100, 6);

        assertTrue(labels.contains("0.01"), labels.toString());
        assertTrue(labels.contains("1"), labels.toString());
        assertTrue(labels.contains("100"), labels.toString());
    }

    @Test
    @DisplayName("a non-positive range is refused where it is written")
    void zeroIsNotOnTheAxis() {
        assertThrows(IllegalArgumentException.class, () -> LogTicks.of(0, 100, 5));
        assertThrows(IllegalArgumentException.class, () -> LogTicks.of(-1, 100, 5));
    }

    @Test
    @DisplayName("a log scale puts each decade in the same room")
    void theWholePointOfIt() {
        // Which is what a log axis is for: a series that lives at 3 and spikes to
        // 30 000 has, on a linear axis, every interesting reading in the bottom
        // pixel.
        var scale = Scale.log(1, 1000, 300, 0);

        assertEquals(300, scale.at(1), 1e-9);
        assertEquals(200, scale.at(10), 1e-9);
        assertEquals(100, scale.at(100), 1e-9);
        assertEquals(0, scale.at(1000), 1e-9);
    }

    @Test
    @DisplayName("and a pixel maps back to the value that is there")
    void theRoundTrip() {
        var scale = Scale.log(2, 5000, 400, 0);

        for (var value : List.of(2.0, 7.5, 100.0, 999.0, 5000.0)) {
            assertEquals(value, scale.from(scale.at(value)), value * 1e-9, "the round trip at " + value);
        }
        // The middle of a log axis is the geometric mean, not the average --
        // which is what a crosshair reads off a pointer.
        assertEquals(Math.sqrt(2 * 5000), scale.from(200), 1e-6);
    }

    @Test
    @DisplayName("a value a logarithm has no place for has no position")
    void nonPositiveValuesHaveNoPixel() {
        var scale = Scale.log(1, 100, 100, 0);

        // `NaN` rather than an edge: a caller that forgot to filter draws nothing
        // instead of drawing a reading at the bottom of the axis that never
        // happened.
        assertTrue(Double.isNaN(scale.at(0)));
        assertTrue(Double.isNaN(scale.at(-5)));
        assertThrows(IllegalArgumentException.class, () -> Scale.log(0, 100, 100, 0));
    }

    @Test
    @DisplayName("non-positive readings become holes rather than being drawn wrong")
    void positiveOnlyIsAFilter() {
        var resolved = Gaps.resolve(List.of(5.0, 0.0, 20.0, -3.0, 40.0), NullPolicy.GAP);

        var positive = Gaps.positiveOnly(resolved);

        assertTrue(positive.has(0));
        assertTrue(!positive.has(1), "a zero has no logarithm");
        assertTrue(!positive.has(3), "and neither has a negative");
        assertEquals(3, positive.runs().size(), "so the line breaks at each of them");
    }
}
