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

/// A logarithmic value axis — `charts.md` §3.1's "log axis, with correct log tick
/// labelling".
///
/// [LogTicksTest] has the arithmetic. What only a picture answers is whether the
/// chart is *using* it, and what it does with the readings a logarithm has no
/// place for.
class ChartLogTest {

    @BeforeEach
    void setUp() {
        RendererRequirement.enforce();
    }

    /// A series that lives at 3 and spikes to 30 000 — four decades, which is
    /// exactly the shape a linear axis cannot show.
    private static final Series SPIKY = Series.of("rate", 3, 4, 7, 30000, 12, 5, 4);

    private static LineChart line(Series series) {
        return new LineChart(List.of(series), List.of(), plot());
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
        // Markers off on both, so what is measured is the *line's* spread: a dot
        // is three pixels tall wherever it is, and counting them would credit the
        // linear chart with room its readings do not have.
        var linear = pixels(line(SPIKY).markers(Markers.NEVER));
        var logarithmic = pixels(line(SPIKY).logY().markers(Markers.NEVER));

        assertFalse(java.util.Arrays.equals(linear, logarithmic));
        // On a linear axis the six readings between 3 and 12 are all in the
        // bottom pixel or two, and the line is a flat floor with one spike. On a
        // log axis each decade gets the same room, so the data spreads out.
        // Measured rather than guessed at: the six quiet readings get two rows of
        // a 156px plot on a linear axis and thirteen on a log one. A multiple
        // rather than a difference, because what the axis promises is
        // proportional -- each decade the same room — and the absolute numbers
        // depend on how tall the test's chart happens to be.
        assertTrue(
                rowsWithData(logarithmic) >= rowsWithData(linear) * 4,
                "linear used " + rowsWithData(linear) + " rows, log used " + rowsWithData(logarithmic));
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
        var bars = new BarChart(List.of(SPIKY), List.of(), plot());
        var area = new AreaChart(List.of(SPIKY), List.of(), plot());

        assertArrayEquals(pixels(bars), pixels(bars.logY()));
        assertArrayEquals(pixels(area), pixels(area.logY()));
    }

    @Test
    @DisplayName("a series with nothing positive in it falls back rather than throwing")
    void nothingToPlot() {
        // `Scale.log` refuses a non-positive domain, and a chart must not turn
        // that into an exception in a paint pass -- a query can return zeroes.
        var zeroes = Series.of("rate", 0, 0, 0);

        assertArrayEquals(
                pixels(line(zeroes)), pixels(line(zeroes).logY()), "it draws the linear picture rather than failing");
    }

    @Test
    @DisplayName("a threshold that reaches zero takes the linear axis and the whole series with it")
    void aThresholdCanEndTheLogAxis() {
        // The fallback is decided on the readings, and a threshold is part of the
        // domain -- so a limit at zero refuses `Scale.log` just as a series of
        // zeroes does, and it arrives after the readings have been read. The
        // chart used to fall back to linear labelling and keep the
        // positives-only data it had already filtered, which is the empty grid
        // the fallback exists to avoid, punched one reading at a time.
        var withZero = Series.of("rate", 30, 40, 0, 700, 900);
        var floor = Threshold.at(0, Threshold.Level.INFO);

        assertArrayEquals(
                pixels(line(withZero).threshold(floor)),
                pixels(line(withZero).logY().threshold(floor)),
                "a log chart that cannot have a log axis draws the linear picture, data and all");
    }

    @Test
    @DisplayName("a bound that reaches zero does the same")
    void aBoundCanEndTheLogAxis() {
        // The other way the domain arrives at zero after the readings have been
        // read: an axis that is a definition rather than an observation.
        var withZero = Series.of("rate", 30, 40, 0, 700, 900);

        assertArrayEquals(
                pixels(line(withZero).axis(0, 1000)),
                pixels(line(withZero).logY().axis(0, 1000)),
                "a hard bound at zero is a linear axis, and the readings are not filtered for one");
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

        GoldenImage.assertMatches(
                "line-chart-log-dark", WIDTH, HEIGHT, 1.0f, frame -> BoxPainter.paint(frame, render.render(tree)));
    }
}
