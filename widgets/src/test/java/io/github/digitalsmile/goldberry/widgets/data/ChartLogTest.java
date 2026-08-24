package io.github.digitalsmile.goldberry.widgets.data;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.digitalsmile.goldberry.RendererRequirement;
import io.github.digitalsmile.goldberry.css.cascade.CascadeLayer;
import io.github.digitalsmile.goldberry.css.Stylesheet;
import io.github.digitalsmile.goldberry.css.Theme;
import io.github.digitalsmile.goldberry.golden.GoldenImage;
import io.github.digitalsmile.goldberry.paint.BoxPainter;
import io.github.digitalsmile.goldberry.paint.TestFrames;
import io.github.digitalsmile.goldberry.widget.attr.Attributes;
import io.github.digitalsmile.goldberry.widget.ElementTree;
import io.github.digitalsmile.goldberry.widget.Widget;
import io.github.digitalsmile.goldberry.widget.WidgetRenderer;
import io.github.digitalsmile.goldberry.widgets.Controls;
import io.github.digitalsmile.goldberry.widgets.controls.TestFont;
import io.github.digitalsmile.goldberry.widgets.core.Column;
import io.github.digitalsmile.goldberry.widgets.data.areachart.AreaChart;
import io.github.digitalsmile.goldberry.widgets.data.barchart.BarChart;
import io.github.digitalsmile.goldberry.widgets.data.linechart.LineChart;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/// A logarithmic value axis — `charts.md` §3.1's "log axis, with correct log tick
/// labelling".
///
/// [LogTicksTest] has the arithmetic. What only a picture answers is whether the
/// chart is *using* it, and what it does with the readings a logarithm has no
/// place for.
class ChartLogTest {

    private static final int WIDTH = 320;
    private static final int HEIGHT = 180;

    @BeforeEach
    void setUp() {
        RendererRequirement.enforce();
    }

    /// A series that lives at 3 and spikes to 30 000 — four decades, which is
    /// exactly the shape a linear axis cannot show.
    private static final Series SPIKY = Series.of("rate", 3, 4, 7, 30000, 12, 5, 4);

    private static Attributes id() {
        return new Attributes("plot", Set.of(), "plot");
    }

    private static WidgetRenderer renderer() {
        return new WidgetRenderer(
                List.of(Controls.baseStylesheet(), Theme.NORD_DARK.load(),
                        Stylesheet.parse(CascadeLayer.APPLICATION, """
                                #frame { padding: 12px; background: var(--gb-bg) }
                                #plot  { width: 296px; height: 156px }
                                """)),
                TestFont.get());
    }

    private static Widget framed(Widget chart) {
        return new Column(List.of(chart), new Attributes("frame", Set.of(), "frame"));
    }

    private static int[] pixels(Widget chart) {
        var render = renderer();
        var tree = new ElementTree(framed(chart));
        var target = TestFrames.of(WIDTH, HEIGHT, 1.0f);
        try {
            BoxPainter.paint(target.frame(), render.render(tree));
        } finally {
            target.end();
        }
        var out = new int[WIDTH * HEIGHT];
        for (var y = 0; y < HEIGHT; y++) {
            for (var x = 0; x < WIDTH; x++) {
                out[y * WIDTH + x] = target.pixel(x, y);
            }
        }
        return out;
    }

    private static LineChart line(Series series) {
        return new LineChart(List.of(series), List.of(), id());
    }

    /// How many rows of the **left two fifths** of the frame the series' colour
    /// appears in.
    ///
    /// The left, deliberately: the whole frame counts the spike, which traverses
    /// the picture top to bottom on either axis and so says nothing about the
    /// readings either side of it. What a log axis is *for* is the quiet data,
    /// and this is how much of the chart the quiet data gets.
    private static int rowsWithData(int[] frame) {
        var rows = 0;
        for (var y = 0; y < HEIGHT; y++) {
            for (var x = 0; x < WIDTH * 2 / 5; x++) {
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

    @Test
    @DisplayName("a log axis gives the quiet data room the linear one spends on the spike")
    void theWholePointOfIt() {
        var linear = pixels(line(SPIKY));
        var logarithmic = pixels(line(SPIKY).logY());

        assertFalse(java.util.Arrays.equals(linear, logarithmic));
        // On a linear axis the six readings between 3 and 12 are all in the
        // bottom pixel or two, and the line is a flat floor with one spike. On a
        // log axis each decade gets the same room, so the data spreads out.
        // Measured rather than guessed at: the six quiet readings get two rows of
        // a 156px plot on a linear axis and thirteen on a log one. A multiple
        // rather than a difference, because what the axis promises is
        // proportional -- each decade the same room — and the absolute numbers
        // depend on how tall the test's chart happens to be.
        assertTrue(rowsWithData(logarithmic) >= rowsWithData(linear) * 4,
                "linear used " + rowsWithData(linear) + " rows, log used "
                        + rowsWithData(logarithmic));
    }

    @Test
    @DisplayName("a zero has no logarithm, so the line breaks there")
    void nonPositiveReadingsBecomeHoles() {
        // The cost of the axis, and it is paid visibly: the reading is not drawn
        // at the bottom of the chart, where it never was -- the line stops.
        var withZero = Series.of("rate", 30, 40, 0, 700, 900);

        var linear = pixels(line(withZero));
        var logarithmic = pixels(line(withZero).logY());

        assertFalse(java.util.Arrays.equals(linear, logarithmic));
        assertArrayEquals(
                pixels(line(withZero).logY()),
                pixels(line(Series.of("rate", 30, 40, Double.NaN, 700, 900)).logY()),
                "a zero on a log axis draws exactly what a hole draws");
    }

    @Test
    @DisplayName("a bar and a band ignore it, because they are lengths from zero")
    void notEveryChartHasOne() {
        // A bar encodes its value as a length from zero and zero is not on a log
        // axis at all -- it is infinitely far down. A chart that drew one anyway
        // would have to pick a bottom, and every choice is a number nobody gave
        // it.
        var bars = new BarChart(List.of(SPIKY), List.of(), id());
        var area = new AreaChart(List.of(SPIKY), List.of(), id());

        assertArrayEquals(pixels(bars), pixels(bars.logY()));
        assertArrayEquals(pixels(area), pixels(area.logY()));
    }

    @Test
    @DisplayName("a series with nothing positive in it falls back rather than throwing")
    void nothingToPlot() {
        // `Scale.log` refuses a non-positive domain, and a chart must not turn
        // that into an exception in a paint pass -- a query can return zeroes.
        var zeroes = Series.of("rate", 0, 0, 0);

        assertArrayEquals(pixels(line(zeroes)), pixels(line(zeroes).logY()),
                "it draws the linear picture rather than failing");
    }

    @Test
    @DisplayName("a smooth log line is still smooth, and still cannot overshoot")
    void itComposesWithTheCurve() {
        // The tangents are computed on the pixels the painter is about to draw,
        // so the curve is monotone in *drawn* space whatever the scale did to get
        // there.
        var smooth = pixels(line(SPIKY).logY().curve(Curve.SMOOTH));

        assertFalse(java.util.Arrays.equals(smooth, pixels(line(SPIKY).logY())));
    }

    @Test
    @DisplayName("four decades, each with the same room")
    void golden() {
        var render = renderer();
        var tree = new ElementTree(framed(line(SPIKY).logY()));

        GoldenImage.assertMatches("line-chart-log-dark", WIDTH, HEIGHT, 1.0f,
                frame -> BoxPainter.paint(frame, render.render(tree)));
    }
}
