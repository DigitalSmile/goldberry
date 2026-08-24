package io.github.digitalsmile.goldberry.widgets.data;

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
import io.github.digitalsmile.goldberry.widgets.data.linechart.LineChart;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/// What each [NullPolicy] looks like — `charts.md` §3.1's "a gap drawn as zero is
/// a lie about the data and the default is the gap".
///
/// [GapsTest] has the arithmetic. These are the pictures, and one of them is the
/// assertion that matters most: **the three policies must not draw the same
/// thing.** A chart whose null handling was wired up but never applied would pass
/// every unit test in the other file.
class ChartGapsTest {

    private static final int WIDTH = 320;
    private static final int HEIGHT = 180;

    @BeforeEach
    void setUp() {
        RendererRequirement.enforce();
    }

    /// A week with two readings missing — one in the middle and one next to the
    /// end, which are the two cases that break differently.
    private static List<Series> holed() {
        return List.of(Series.of("Downloads",
                12, 19, Double.NaN, 27, 31, Double.NaN, 36));
    }

    private static final List<String> DAYS =
            List.of("Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun");

    private static Widget framed(Widget chart) {
        return new Column(List.of(chart), new Attributes("frame", Set.of(), "frame"));
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

    private static Attributes id() {
        return new Attributes("plot", Set.of(), "plot");
    }

    /// Paints `chart` and reads the frame back.
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

    @Test
    @DisplayName("the three policies draw three different pictures")
    void thePolicyIsApplied() {
        var gap = pixels(new LineChart(holed(), DAYS, id()));
        var connect = pixels(new LineChart(holed(), DAYS, id()).nulls(NullPolicy.CONNECT));
        var zero = pixels(new LineChart(holed(), DAYS, id()).nulls(NullPolicy.ZERO));

        assertFalse(java.util.Arrays.equals(gap, connect),
                "a hole and a line across it are different claims about the data");
        assertFalse(java.util.Arrays.equals(gap, zero),
                "a hole and a zero are different claims about the data");
        assertFalse(java.util.Arrays.equals(connect, zero),
                "and so are a line across it and a dive to the baseline");
    }

    @Test
    @DisplayName("the default is the gap, which invents nothing")
    void gapIsWhatYouGetForFree() {
        assertTrue(java.util.Arrays.equals(
                        pixels(new LineChart(holed(), DAYS, id())),
                        pixels(new LineChart(holed(), DAYS, id()).nulls(NullPolicy.GAP))),
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
        var holed = pixels(new LineChart(holed(), DAYS, id()));
        var whole = pixels(new LineChart(
                List.of(Series.of("Downloads", 12, 19, 23, 27, 31, 33, 36)), DAYS, id()));

        var differing = 0;
        for (var i = 0; i < holed.length; i++) {
            if (holed[i] != whole[i]) {
                differing++;
            }
        }
        // A tenth of the frame is generous for two missing segments out of six and
        // nowhere near the whole picture a collapsed axis would redraw.
        assertTrue(differing > 0 && differing < holed.length / 10,
                "expected a local difference, and " + differing + " pixels changed");
    }

    @Test
    @DisplayName("a broken line, which is what a missing reading looks like")
    void gapGolden() {
        var render = renderer();
        var tree = new ElementTree(framed(new LineChart(holed(), DAYS, id())));

        GoldenImage.assertMatches("line-chart-gap-dark", WIDTH, HEIGHT, 1.0f,
                frame -> BoxPainter.paint(frame, render.render(tree)));
    }

    @Test
    @DisplayName("a line straight across it, for a series that was merely not scraped")
    void connectGolden() {
        var render = renderer();
        var tree = new ElementTree(framed(
                new LineChart(holed(), DAYS, id()).nulls(NullPolicy.CONNECT)));

        GoldenImage.assertMatches("line-chart-connect-dark", WIDTH, HEIGHT, 1.0f,
                frame -> BoxPainter.paint(frame, render.render(tree)));
    }

    @Test
    @DisplayName("a dive to the baseline, which is a claim and looks like one")
    void zeroGolden() {
        var render = renderer();
        var tree = new ElementTree(framed(
                new LineChart(holed(), DAYS, id()).nulls(NullPolicy.ZERO)));

        GoldenImage.assertMatches("line-chart-zero-dark", WIDTH, HEIGHT, 1.0f,
                frame -> BoxPainter.paint(frame, render.render(tree)));
    }

    @Test
    @DisplayName("a stack breaks where any of its components is missing")
    void stackedAreaGolden() {
        var render = renderer();
        var tree = new ElementTree(framed(new AreaChart(List.of(
                Series.of("Cache", 40, 52, 48, 61, 58, 66, 71),
                Series.of("Origin", 12, Double.NaN, 9, 18, 21, 19, 24)), DAYS, id())));

        // The upper band breaks too, and that is the point: a total with an
        // unknown component is unknown, and drawing the band above the hole as
        // though the missing one were zero would put it at a height nobody
        // reported.
        GoldenImage.assertMatches("area-chart-gap-dark", WIDTH, HEIGHT, 1.0f,
                frame -> BoxPainter.paint(frame, render.render(tree)));
    }
}
