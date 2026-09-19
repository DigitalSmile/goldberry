package io.github.digitalsmile.goldberry.widgets.data;

import static io.github.digitalsmile.goldberry.widgets.data.ChartFrame.HEIGHT;
import static io.github.digitalsmile.goldberry.widgets.data.ChartFrame.WIDTH;
import static io.github.digitalsmile.goldberry.widgets.data.ChartFrame.framed;
import static io.github.digitalsmile.goldberry.widgets.data.ChartFrame.pixels;
import static io.github.digitalsmile.goldberry.widgets.data.ChartFrame.plot;
import static io.github.digitalsmile.goldberry.widgets.data.ChartFrame.renderer;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
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

/// What each [Curve] draws — `charts.md` §3.1's "interpolation: linear, smooth,
/// step".
///
/// [CurvesTest] proves the property that matters about `SMOOTH` and needs no
/// renderer to do it. This is the half only a picture can answer: that the chart
/// actually asked for the curve, and that a band's two edges agree.
class ChartCurveTest {

    @BeforeEach
    void setUp() {
        RendererRequirement.enforce();
    }

    /// A shape with a sharp change in it, which is where the three differ most.
    private static final Series STEPPY = Series.of("state", 10, 10, 90, 90, 40, 40, 70);

    private static LineChart line() {
        return new LineChart(List.of(STEPPY), List.of(), plot());
    }

    @Test
    @DisplayName("the three curves draw three different pictures")
    void theCurveIsApplied() {
        var linear = pixels(line());
        var smooth = pixels(line().curve(Curve.SMOOTH));
        var step = pixels(line().curve(Curve.STEP));

        assertFalse(java.util.Arrays.equals(linear, smooth));
        assertFalse(java.util.Arrays.equals(linear, step));
        assertFalse(java.util.Arrays.equals(smooth, step));
    }

    @Test
    @DisplayName("linear is what a chart draws when nobody says otherwise")
    void theDefaultIsTheWeakestClaim() {
        // The weakest claim about what happened between two readings, which is
        // the one a chart should make without being asked.
        assertArrayEquals(pixels(line()), pixels(line().curve(Curve.LINEAR)));
    }

    @Test
    @DisplayName("a step holds its value, so there is a column of ink under each jump")
    void stepIsHoldThenJump() {
        // The visible consequence of holding forward: at the x where the value
        // jumps there is a *vertical* run of the series colour, taller than a
        // sloped line could make at one x. A linear chart of the same data has
        // no such column.
        assertTrue(
                tallestColumn(pixels(line().curve(Curve.STEP))) > tallestColumn(pixels(line())) + 20,
                "expected a vertical jump the linear chart does not have");
    }

    /// The tallest run of series-coloured pixels in any single column.
    private static int tallestColumn(int[] frame) {
        var tallest = 0;
        for (var x = 0; x < WIDTH; x++) {
            var run = 0;
            for (var y = 0; y < HEIGHT; y++) {
                var pixel = frame[y * WIDTH + x];
                var red = (pixel >> 16) & 0xFF;
                var green = (pixel >> 8) & 0xFF;
                var blue = pixel & 0xFF;
                if (green > red + 20 && green > blue + 20) {
                    run++;
                    tallest = Math.max(tallest, run);
                } else {
                    run = 0;
                }
            }
        }
        return tallest;
    }

    @Test
    @DisplayName("a bar chart ignores it, because a bar is a length rather than a path")
    void barsHaveNoCurve() {
        var bars = new BarChart(List.of(STEPPY), List.of(), plot());

        assertArrayEquals(pixels(bars), pixels(bars.curve(Curve.SMOOTH)));
        assertArrayEquals(pixels(bars), pixels(bars.curve(Curve.STEP)));
    }

    @Test
    @DisplayName("a band takes the curve too, on both of its edges")
    void bandsCurveWithTheirLines() {
        var area = new AreaChart(
                List.of(Series.of("Cache", 40, 52, 44, 61, 58, 66, 71), Series.of("Origin", 12, 9, 15, 11, 14, 10, 13)),
                List.of(),
                plot());

        // The upper band's underside is the lower band's top. If one were curved
        // and the other straight the fill between them would be thicker than the
        // numbers wherever the curve bulged -- a band that overstates itself.
        assertFalse(java.util.Arrays.equals(pixels(area), pixels(area.curve(Curve.SMOOTH))));
    }

    @Test
    @DisplayName("a smooth line through a sharp change")
    void smoothGolden() {
        var render = renderer();
        var tree = new ElementTree(framed(line().curve(Curve.SMOOTH)));

        GoldenImage.assertMatches(
                "line-chart-smooth-dark", WIDTH, HEIGHT, 1.0f, frame -> BoxPainter.paint(frame, render.render(tree)));
    }

    @Test
    @DisplayName("a step, for a series that is a state rather than a measurement")
    void stepGolden() {
        var render = renderer();
        var tree = new ElementTree(framed(line().curve(Curve.STEP)));

        GoldenImage.assertMatches(
                "line-chart-step-dark", WIDTH, HEIGHT, 1.0f, frame -> BoxPainter.paint(frame, render.render(tree)));
    }

    @Test
    @DisplayName("a stack whose bands are smooth and still nest")
    void smoothAreaGolden() {
        var render = renderer();
        var tree = new ElementTree(framed(new AreaChart(
                        List.of(
                                Series.of("Cache", 40, 52, 44, 61, 58, 66, 71),
                                Series.of("Origin", 12, 9, 15, 11, 14, 10, 13)),
                        List.of(),
                        plot())
                .curve(Curve.SMOOTH)));

        GoldenImage.assertMatches(
                "area-chart-smooth-dark", WIDTH, HEIGHT, 1.0f, frame -> BoxPainter.paint(frame, render.render(tree)));
    }
}
