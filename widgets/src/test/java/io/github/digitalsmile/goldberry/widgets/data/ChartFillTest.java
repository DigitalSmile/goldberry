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

/// What each [Fill] draws — `charts.md` §3.1's "fill opacity, gradient fill",
/// which was the last row of that table left unbuilt (ADR-0207).
///
/// [io.github.digitalsmile.goldberry.natives.blend2d.BlendGradientTest] proves
/// what the six new symbols put in a buffer. This is the half only a chart can
/// answer: that the ramp is anchored to the *data* rather than to the plot, that
/// it runs down rather than across, and that a chart nobody asked keeps exactly
/// the picture it had.
class ChartFillTest {

    private static final int WIDTH = 320;
    private static final int HEIGHT = 180;

    @BeforeEach
    void setUp() {
        RendererRequirement.enforce();
    }

    /// A shape with a clear peak, so "the fill is thickest under the peak" is a
    /// thing a column count can see.
    private static final Series TRAFFIC = Series.of("Requests", 20, 45, 38, 72, 64, 88, 70);

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

    private static LineChart line() {
        return new LineChart(List.of(TRAFFIC), List.of(), id());
    }

    private static AreaChart area() {
        return new AreaChart(List.of(
                Series.of("Cache", 40, 52, 44, 61, 58, 66, 71),
                Series.of("Origin", 12, 9, 15, 11, 14, 10, 13)), List.of(), id());
    }

    @Test
    @DisplayName("a line chart nobody asked keeps exactly the picture it had")
    void noneIsTheDefault() {
        // The reason NONE is the default rather than SOLID: this feature must
        // not have happened to anybody's dashboard.
        assertArrayEquals(pixels(line()), pixels(line().fill(Fill.NONE)));
    }

    @Test
    @DisplayName("a gradient puts ink under the line where there was none")
    void aGradientFillsUnderTheLine() {
        var bare = tinted(pixels(line()));
        var filled = tinted(pixels(line().fill(Fill.GRADIENT)));

        assertTrue(filled > bare * 3,
                () -> "a fill covers far more than the 2px line does: " + bare + " then "
                        + filled);
    }

    @Test
    @DisplayName("the fade thins downward, which is the whole claim of a gradient")
    void theRampRunsDown() {
        // The one property that distinguishes a gradient from a flat wash, and
        // the one a golden image cannot state in words: near the line the fill
        // is strong, near the baseline it is nearly gone. Counted as tinted
        // pixels per horizontal band rather than sampled at a point, so it does
        // not depend on where any particular reading landed.
        var frame = pixels(line().fill(Fill.GRADIENT));
        var upper = tintedInRows(frame, 60, 90);
        var lower = tintedInRows(frame, 120, 150);

        assertTrue(upper > lower,
                () -> "the fill is heavier near the line than near the baseline: " + upper
                        + " then " + lower);
    }

    @Test
    @DisplayName("a flat wash and a fade are different pictures")
    void solidIsNotGradient() {
        assertFalse(java.util.Arrays.equals(
                pixels(line().fill(Fill.SOLID)), pixels(line().fill(Fill.GRADIENT))));
    }

    @Test
    @DisplayName("an area chart reads NONE as a flat wash, because a band with no fill is not one")
    void anAreaChartRefusesToDrawNothing() {
        assertArrayEquals(pixels(area()), pixels(area().fill(Fill.NONE)));
        assertArrayEquals(pixels(area()), pixels(area().fill(Fill.SOLID)));
    }

    @Test
    @DisplayName("an area chart's bands can fade, and each within its own extent")
    void aBandFadesWithinItself() {
        var solid = pixels(area());
        var faded = pixels(area().fill(Fill.GRADIENT));

        assertFalse(java.util.Arrays.equals(solid, faded), "the fill actually changed");
        // **Both bands reach full strength somewhere, and that is the whole
        // claim.** A ramp anchored to the *plot* would start at the top of the
        // chart, so the upper band would be strong and the lower one -- which
        // begins halfway down -- would already be half gone before it started.
        // Anchored to each band's own extent, both are drawn at full strength
        // along their own top edge and fade from there.
        //
        // Slot 1 is a green and slot 2 a purple, which is what makes the two
        // separable in one frame without knowing where either band is.
        assertTrue(peak(faded, ChartFillTest::green) > peak(solid, ChartFillTest::green) * 0.8,
                "the lower band still reaches its own colour at its top edge");
        assertTrue(peak(faded, ChartFillTest::purple) > peak(solid, ChartFillTest::purple) * 0.8,
                "and so does the upper one, which a plot-wide ramp would have thinned");
    }

    @Test
    @DisplayName("a bar chart has no fill to set, and setting one through the options does nothing")
    void barsHaveNoFill() {
        // A bar is a length from zero drawn as a solid rectangle; there is no
        // region between it and anything for a ramp to cross. `BarChart` has no
        // `fill` wither at all -- this is the other half of that, which is that
        // reaching past it through `ChartOptions` changes no pixel.
        var bars = new BarChart(List.of(TRAFFIC), List.of(), id());

        assertArrayEquals(pixels(bars),
                pixels(bars.options(bars.options().fill(Fill.GRADIENT))));
    }

    @Test
    @DisplayName("a fill follows the curve the line was drawn with")
    void theFillIsSmoothedToo() {
        // Built from the same run of points as the stroke, after smoothing and
        // after downsampling. A fill built from the raw values would show its
        // own straight edges through a smoothed line.
        assertFalse(java.util.Arrays.equals(
                pixels(line().fill(Fill.GRADIENT)),
                pixels(line().fill(Fill.GRADIENT).curve(Curve.SMOOTH))));
    }

    @Test
    @DisplayName("a fade under a line")
    void lineGradientGolden() {
        var render = renderer();
        var tree = new ElementTree(framed(line().fill(Fill.GRADIENT)));

        GoldenImage.assertMatches("line-chart-gradient-dark", WIDTH, HEIGHT, 1.0f,
                frame -> BoxPainter.paint(frame, render.render(tree)));
    }

    @Test
    @DisplayName("a stack whose bands each fade within themselves")
    void areaGradientGolden() {
        var render = renderer();
        var tree = new ElementTree(framed(area().fill(Fill.GRADIENT)));

        GoldenImage.assertMatches("area-chart-gradient-dark", WIDTH, HEIGHT, 1.0f,
                frame -> BoxPainter.paint(frame, render.render(tree)));
    }

    // --- helpers ------------------------------------------------------------

    /// How many pixels carry the series hue at all.
    ///
    /// Slot 1 is a green, so "green channel clearly ahead of the other two" is
    /// the test — and the threshold is low (6 rather than the 20 a *line* test
    /// would use) precisely because the faint end of a fade is the part being
    /// counted.
    private static int tinted(int[] frame) {
        return tintedInRows(frame, 0, HEIGHT);
    }

    /// The brightest pixel of a given hue, as the sum of its channels.
    ///
    /// A band drawn over the Nord dark surface is lighter than it, so "how far
    /// from the background" and "how bright" are the same number here — and a
    /// faded band is by definition closer to the surface it is drawn on. Zero
    /// when the hue is not in the frame at all, which makes the ratio the
    /// assertions take meaningful rather than accidentally true.
    private static int peak(int[] frame, java.util.function.IntPredicate hue) {
        var brightest = 0;
        for (var pixel : frame) {
            if (!hue.test(pixel)) {
                continue;
            }
            var sum = ((pixel >> 16) & 0xFF) + ((pixel >> 8) & 0xFF) + (pixel & 0xFF);
            brightest = Math.max(brightest, sum);
        }
        return brightest;
    }

    private static boolean green(int pixel) {
        var red = (pixel >> 16) & 0xFF;
        var g = (pixel >> 8) & 0xFF;
        var blue = pixel & 0xFF;
        return g > red + 6 && g > blue + 6;
    }

    private static boolean purple(int pixel) {
        var red = (pixel >> 16) & 0xFF;
        var g = (pixel >> 8) & 0xFF;
        var blue = pixel & 0xFF;
        return red > g + 6 && blue > g + 6;
    }

    private static int tintedInRows(int[] frame, int fromRow, int toRow) {
        var count = 0;
        for (var y = fromRow; y < Math.min(toRow, HEIGHT); y++) {
            for (var x = 0; x < WIDTH; x++) {
                var pixel = frame[y * WIDTH + x];
                var red = (pixel >> 16) & 0xFF;
                var green = (pixel >> 8) & 0xFF;
                var blue = pixel & 0xFF;
                if (green > red + 6 && green > blue + 6) {
                    count++;
                }
            }
        }
        return count;
    }
}
