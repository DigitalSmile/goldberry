package io.github.digitalsmile.goldberry.widgets.data;

import static io.github.digitalsmile.goldberry.widgets.data.ChartFrame.HEIGHT;
import static io.github.digitalsmile.goldberry.widgets.data.ChartFrame.WIDTH;
import static io.github.digitalsmile.goldberry.widgets.data.ChartFrame.framed;
import static io.github.digitalsmile.goldberry.widgets.data.ChartFrame.pixels;
import static io.github.digitalsmile.goldberry.widgets.data.ChartFrame.plot;
import static io.github.digitalsmile.goldberry.widgets.data.ChartFrame.renderer;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import io.github.digitalsmile.goldberry.RendererRequirement;
import io.github.digitalsmile.goldberry.golden.GoldenImage;
import io.github.digitalsmile.goldberry.paint.BoxPainter;
import io.github.digitalsmile.goldberry.widget.ElementTree;
import io.github.digitalsmile.goldberry.widgets.data.areachart.AreaChart;
import io.github.digitalsmile.goldberry.widgets.data.barchart.BarChart;
import io.github.digitalsmile.goldberry.widgets.data.linechart.LineChart;

/// What the axis has to reach, and whether each reading is marked — `charts.md`
/// §3.1's "axis min/max, soft min/max" and "point markers".
class ChartAxisTest {

    @BeforeEach
    void setUp() {
        RendererRequirement.enforce();
    }

    /// An uptime: four readings inside a tenth of a percent of each other.
    private static final Series UPTIME = Series.of("uptime", 99.94, 99.97, 99.91, 99.99);

    /// How many rows the series' colour appears in — how much of the plot the
    /// data is using.
    private static int rowsWithData(int[] frame) {
        var rows = 0;
        for (var y = 0; y < HEIGHT; y++) {
            for (var x = 0; x < WIDTH; x++) {
                var pixel = frame[y * WIDTH + x];
                var red = (pixel >> 16) & 0xFF;
                var green = (pixel >> 8) & 0xFF;
                var blue = pixel & 0xFF;
                if (green > red + 20 && green > blue + 20) {
                    rows++;
                    break;
                }
            }
        }
        return rows;
    }

    private static LineChart line(Series series) {
        return new LineChart(List.of(series), List.of(), plot());
    }

    @Test
    @DisplayName("a soft bound stops a flat series rendering as noise")
    void theWholePointOfSoftBounds() {
        // Auto-scaled, eight hundredths of a percent fill the plot: a mountain
        // range whose peaks and troughs are the same number to any reader. The
        // picture says "look at this" about noise, and loudest when the news is
        // good.
        var scaled = rowsWithData(pixels(line(UPTIME).markers(Markers.NEVER)));
        var bounded = rowsWithData(pixels(line(UPTIME).softAxis(99, 100).markers(Markers.NEVER)));

        assertTrue(scaled > bounded * 3, "auto-scaled used " + scaled + " rows, soft-bounded used " + bounded);
    }

    @Test
    @DisplayName("and it still gets out of the way when something happens")
    void softIsAFloorRatherThanACage() {
        // A soft bound is "at least this", so an outage pushes the axis down to
        // meet it. A chart that hid the one reading anybody needed would be worse
        // than the noisy one.
        var outage = Series.of("uptime", 99.94, 99.97, 40, 99.99);

        assertFalse(
                java.util.Arrays.equals(
                        pixels(line(outage).softAxis(99, 100)),
                        pixels(line(UPTIME).softAxis(99, 100))),
                "the outage is on the chart");
        assertEquals(40.0, Bounds.soft(99, 100).applyMin(40));
        assertEquals(100.0, Bounds.soft(99, 100).applyMax(99.99));
    }

    @Test
    @DisplayName("a hard bound does not move for the data")
    void hardIsAPromise() {
        assertEquals(
                99.0, Bounds.hard(99, 100).applyMin(40), "which is the correct rendering of a promise that was wrong");
        assertEquals(100.0, Bounds.hard(99, 100).applyMax(400));

        // And the two are different pictures, so `axis` and `softAxis` cannot be
        // confused for each other by a chart that ignores the flag.
        var outage = Series.of("uptime", 99.94, 99.97, 40, 99.99);
        assertFalse(java.util.Arrays.equals(
                pixels(line(outage).axis(99, 100)), pixels(line(outage).softAxis(99, 100))));
    }

    @Test
    @DisplayName("an unset bound is the data's own range")
    void noneIsNotZero() {
        assertEquals(7.0, Bounds.NONE.applyMin(7));
        assertEquals(9.0, Bounds.NONE.applyMax(9));
        assertFalse(Bounds.NONE.isSet());
        assertArrayEquals(
                pixels(line(UPTIME)), pixels(line(UPTIME).options(ChartOptions.DEFAULTS.bounds(Bounds.NONE))));
    }

    @Test
    @DisplayName("a bound written backwards is read the way it was meant")
    void theEndsAreASet() {
        assertEquals(Bounds.soft(0, 100), Bounds.soft(100, 0));
    }

    @Test
    @DisplayName("markers appear when there is room and vanish when there is not")
    void autoMeasuresRatherThanAssumes() {
        var few = Series.of("rate", 3, 9, 4, 8, 5, 7, 6);
        var many = new java.util.ArrayList<Double>();
        for (var i = 0; i < 400; i++) {
            many.add(3.0 + (i % 7));
        }

        // The same chart, at seven readings and at four hundred. What makes a
        // dotted mess is how close the dots are on screen, so the rule is in
        // pixels rather than in points.
        assertFalse(
                java.util.Arrays.equals(pixels(line(few)), pixels(line(few).markers(Markers.NEVER))),
                "seven readings have room for their dots");
        assertArrayEquals(
                pixels(new LineChart(List.of(new Series("rate", many)), List.of(), plot())),
                pixels(new LineChart(List.of(new Series("rate", many)), List.of(), plot()).markers(Markers.NEVER)),
                "four hundred do not, and AUTO draws none");
    }

    @Test
    @DisplayName("always and never say so whatever the spacing")
    void theOtherTwoAreNotSuggestions() {
        var many = new java.util.ArrayList<Double>();
        for (var i = 0; i < 400; i++) {
            many.add(3.0 + (i % 7));
        }
        var crowded = new LineChart(List.of(new Series("rate", many)), List.of(), plot());

        assertFalse(
                java.util.Arrays.equals(
                        pixels(crowded.markers(Markers.ALWAYS)), pixels(crowded.markers(Markers.NEVER))),
                "ALWAYS draws them into the mess it was warned about");
    }

    /// `bar-chart` and `area-chart` carry this knob because all three axis charts
    /// carry the same ones, and both ignore it: a bar's shape is its length and a
    /// band's is its thickness, so neither has anywhere to put a dot that would
    /// mean anything. Their withers said otherwise until [ChartSpec] was given one
    /// statement per knob — `BarChart.markers` promised "a dot at each reading" and
    /// there has never been one.
    @Test
    @DisplayName("a bar and a band have nowhere to put one, and say the same either way")
    void notEveryChartMarksItsReadings() {
        var readings = Series.of("rate", 3, 9, 4, 8, 5, 7, 6);
        var bars = new BarChart(List.of(readings), List.of(), plot());
        var area = new AreaChart(List.of(readings), List.of(), plot());

        assertArrayEquals(pixels(bars), pixels(bars.markers(Markers.ALWAYS)));
        assertArrayEquals(pixels(area), pixels(area.markers(Markers.ALWAYS)));
    }

    @Test
    @DisplayName("a flat line near the top, which is what an uptime is")
    void golden() {
        var render = renderer();
        var tree = new ElementTree(framed(line(UPTIME).softAxis(99, 100)));

        GoldenImage.assertMatches(
                "line-chart-soft-dark", WIDTH, HEIGHT, 1.0f, frame -> BoxPainter.paint(frame, render.render(tree)));
    }
}
