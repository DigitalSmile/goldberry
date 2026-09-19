package io.github.digitalsmile.goldberry.widgets.data;

import static io.github.digitalsmile.goldberry.widgets.data.ChartFrame.HEIGHT;
import static io.github.digitalsmile.goldberry.widgets.data.ChartFrame.WIDTH;
import static io.github.digitalsmile.goldberry.widgets.data.ChartFrame.framed;
import static io.github.digitalsmile.goldberry.widgets.data.ChartFrame.pixels;
import static io.github.digitalsmile.goldberry.widgets.data.ChartFrame.plot;
import static io.github.digitalsmile.goldberry.widgets.data.ChartFrame.renderer;
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
import io.github.digitalsmile.goldberry.widgets.data.linechart.LineChart;

/// What each [NullPolicy] looks like — `charts.md` §3.1's "a gap drawn as zero is
/// a lie about the data and the default is the gap".
///
/// [GapsTest] has the arithmetic. These are the pictures, and one of them is the
/// assertion that matters most: **the three policies must not draw the same
/// thing.** A chart whose null handling was wired up but never applied would pass
/// every unit test in the other file.
class ChartGapsTest {

    @BeforeEach
    void setUp() {
        RendererRequirement.enforce();
    }

    /// A week with two readings missing — one in the middle and one next to the
    /// end, which are the two cases that break differently.
    private static List<Series> holed() {
        return List.of(Series.of("Downloads", 12, 19, Double.NaN, 27, 31, Double.NaN, 36));
    }

    private static final List<String> DAYS = List.of("Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun");

    @Test
    @DisplayName("the three policies draw three different pictures")
    void thePolicyIsApplied() {
        var gap = pixels(new LineChart(holed(), DAYS, plot()));
        var connect = pixels(new LineChart(holed(), DAYS, plot()).nulls(NullPolicy.CONNECT));
        var zero = pixels(new LineChart(holed(), DAYS, plot()).nulls(NullPolicy.ZERO));

        assertFalse(
                java.util.Arrays.equals(gap, connect),
                "a hole and a line across it are different claims about the data");
        assertFalse(java.util.Arrays.equals(gap, zero), "a hole and a zero are different claims about the data");
        assertFalse(java.util.Arrays.equals(connect, zero), "and so are a line across it and a dive to the baseline");
    }

    @Test
    @DisplayName("the default is the gap, which invents nothing")
    void gapIsWhatYouGetForFree() {
        assertTrue(
                java.util.Arrays.equals(
                        pixels(new LineChart(holed(), DAYS, plot())),
                        pixels(new LineChart(holed(), DAYS, plot()).nulls(NullPolicy.GAP))),
                "asking for a gap and asking for nothing are the same request");
    }

    @Test
    @DisplayName("a hole does not collapse the chart it is in")
    void aHoleIsNotADomain() {
        // `Math.min` propagates NaN, so a series with one missing reading used to
        // answer NaN for its domain -- and the axis, finding it was not finite,
        // fell back to 0..0 and drew every point on one line. The picture with a
        // hole and the picture without the holed points must therefore differ
        // *only* where the hole is, which is enough to say the axis survived: a
        // collapsed chart differs everywhere.
        var holed = pixels(new LineChart(holed(), DAYS, plot()));
        var whole = pixels(new LineChart(List.of(Series.of("Downloads", 12, 19, 23, 27, 31, 33, 36)), DAYS, plot()));

        var differing = 0;
        for (var i = 0; i < holed.length; i++) {
            if (holed[i] != whole[i]) {
                differing++;
            }
        }
        // A tenth of the frame is generous for two missing segments out of six and
        // nowhere near the whole picture a collapsed axis would redraw.
        assertTrue(
                differing > 0 && differing < holed.length / 10,
                "expected a local difference, and " + differing + " pixels changed");
    }

    @Test
    @DisplayName("a broken line, which is what a missing reading looks like")
    void gapGolden() {
        var render = renderer();
        var tree = new ElementTree(framed(new LineChart(holed(), DAYS, plot())));

        GoldenImage.assertMatches(
                "line-chart-gap-dark", WIDTH, HEIGHT, 1.0f, frame -> BoxPainter.paint(frame, render.render(tree)));
    }

    @Test
    @DisplayName("a line straight across it, for a series that was merely not scraped")
    void connectGolden() {
        var render = renderer();
        var tree = new ElementTree(framed(new LineChart(holed(), DAYS, plot()).nulls(NullPolicy.CONNECT)));

        GoldenImage.assertMatches(
                "line-chart-connect-dark", WIDTH, HEIGHT, 1.0f, frame -> BoxPainter.paint(frame, render.render(tree)));
    }

    @Test
    @DisplayName("a dive to the baseline, which is a claim and looks like one")
    void zeroGolden() {
        var render = renderer();
        var tree = new ElementTree(framed(new LineChart(holed(), DAYS, plot()).nulls(NullPolicy.ZERO)));

        GoldenImage.assertMatches(
                "line-chart-zero-dark", WIDTH, HEIGHT, 1.0f, frame -> BoxPainter.paint(frame, render.render(tree)));
    }

    @Test
    @DisplayName("a stack breaks where any of its components is missing")
    void stackedAreaGolden() {
        var render = renderer();
        var tree = new ElementTree(framed(new AreaChart(
                List.of(
                        Series.of("Cache", 40, 52, 48, 61, 58, 66, 71),
                        Series.of("Origin", 12, Double.NaN, 9, 18, 21, 19, 24)),
                DAYS,
                plot())));

        // The upper band breaks too, and that is the point: a total with an
        // unknown component is unknown, and drawing the band above the hole as
        // though the missing one were zero would put it at a height nobody
        // reported.
        GoldenImage.assertMatches(
                "area-chart-gap-dark", WIDTH, HEIGHT, 1.0f, frame -> BoxPainter.paint(frame, render.render(tree)));
    }
}
