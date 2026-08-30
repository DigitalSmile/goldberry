package io.github.digitalsmile.goldberry.widgets.data;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/// A value's place on an axis.
///
/// Small arithmetic, and the reason it is a type with tests rather than four
/// lines in each chart: every one of these cases is a bug that has shipped in
/// somebody's charting library, and the y-axis one has shipped in most of them.
class ScaleTest {

    @Test
    @DisplayName("maps the ends to the ends and the middle to the middle")
    void linearMapping() {
        var scale = Scale.linear(0, 100, 0, 200);

        assertEquals(0.0, scale.at(0));
        assertEquals(200.0, scale.at(100));
        assertEquals(100.0, scale.at(50));
    }

    @Test
    @DisplayName("a y scale is a swapped range, so bigger values are higher up")
    void theYAxisPointsUp() {
        // A frame's y grows downward and a chart's values grow upward. This is
        // the whole of that, and it is why there is no "inverted" flag to forget
        // to set.
        var y = Scale.linear(0, 10, 40, 0);

        assertEquals(40.0, y.at(0), "the smallest value sits at the bottom of the box");
        assertEquals(0.0, y.at(10), "and the largest at the top");
        assertTrue(y.at(9) < y.at(1), "a bigger value is a smaller y");
    }

    @Test
    @DisplayName("does not clamp, because a chart decides that for itself")
    void outOfDomainIsNotPinned() {
        var scale = Scale.linear(0, 100, 0, 200);

        assertEquals(-20.0, scale.at(-10), 1e-9, "below the domain");
        // 220.00000000000003, and the tolerance is the honest answer: the
        // arithmetic is (value - min) / span * range and floating point does not
        // owe anybody a round number. A chart draws at this to a third of a
        // pixel either way.
        assertEquals(220.0, scale.at(110), 1e-9, "and above it");
    }

    @Test
    @DisplayName("a flat domain is the middle, not an edge and not a NaN")
    void flatDomains() {
        var scale = Scale.linear(7, 7, 0, 40);

        assertEquals(20.0, scale.at(7));
        assertEquals(20.0, scale.at(999), "every value is the same value");
    }

    @Test
    @DisplayName("inverts, which is what a crosshair reads")
    void fromIsAtBackwards() {
        var scale = Scale.linear(10, 20, 0, 100);

        assertEquals(15.0, scale.from(50));
        for (var value : new double[] {10, 12.5, 17, 20}) {
            assertEquals(value, scale.from(scale.at(value)), 1e-9);
        }
        // And on a y scale, where the range runs backwards.
        var y = Scale.linear(0, 10, 40, 0);
        assertEquals(10.0, y.from(0), 1e-9);
        assertEquals(0.0, y.from(40), 1e-9);
    }

    @Test
    @DisplayName("a flat range answers rather than dividing by zero")
    void flatRanges() {
        // A chart laid out to nothing: the box is zero wide and something still
        // asks where a value goes.
        var scale = Scale.linear(0, 10, 5, 5);

        assertEquals(5.0, scale.at(3));
        assertEquals(5.0, scale.from(5));
    }

    @Test
    @DisplayName("a nice scale widens the domain to the labels, so gridlines land on them")
    void niceAgreesWithItsLabels() {
        // The bug this prevents: labelling 0..97 as 0,25,50,75,100 while scaling
        // the pixels to 0..97, so every gridline is drawn a few pixels off its
        // own label. One call, so they cannot disagree.
        var scale = Scale.nice(0, 97, 40, 0, 5);
        var labels = scale.labels(5);

        assertEquals(labels.min(), scale.domainMin());
        assertEquals(labels.max(), scale.domainMax());
        assertEquals(0.0, scale.at(labels.max()), 1e-9, "the last label is at the top of the box");
        assertEquals(40.0, scale.at(labels.min()), 1e-9, "and the first at the bottom");
    }

    @Test
    @DisplayName("a nice scale over a flat series keeps the value it was given")
    void niceOverAFlatSeries() {
        var scale = Scale.nice(7, 7, 40, 0, 5);

        assertEquals(7.0, scale.domainMin());
        assertEquals(7.0, scale.domainMax());
        assertEquals(20.0, scale.at(7), "which the flat-domain rule then centres");
    }

    @Test
    @DisplayName("refuses bounds that are not numbers")
    void boundsAreChecked() {
        assertThrows(IllegalArgumentException.class, () -> Scale.linear(0, Double.NaN, 0, 1));
        assertThrows(IllegalArgumentException.class, () -> Scale.linear(0, 1, 0, Double.POSITIVE_INFINITY));
    }
}
