package io.github.digitalsmile.goldberry.widgets.data;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import io.github.digitalsmile.goldberry.RendererRequirement;
import io.github.digitalsmile.goldberry.css.Stylesheet;
import io.github.digitalsmile.goldberry.css.Theme;
import io.github.digitalsmile.goldberry.css.cascade.CascadeLayer;
import io.github.digitalsmile.goldberry.golden.GoldenImage;
import io.github.digitalsmile.goldberry.paint.BoxPainter;
import io.github.digitalsmile.goldberry.paint.TestFrames;
import io.github.digitalsmile.goldberry.widget.ElementTree;
import io.github.digitalsmile.goldberry.widget.Widget;
import io.github.digitalsmile.goldberry.widget.WidgetRenderer;
import io.github.digitalsmile.goldberry.widget.attr.Attributes;
import io.github.digitalsmile.goldberry.widgets.Controls;
import io.github.digitalsmile.goldberry.widgets.controls.TestFont;
import io.github.digitalsmile.goldberry.widgets.core.Column;
import io.github.digitalsmile.goldberry.widgets.data.linechart.LineChart;

/// Limits drawn across a chart — `charts.md` §3.1's "thresholds: lines and shaded
/// regions, drawn in the *semantic* hues, never a series slot".
class ThresholdTest {

    private static final int WIDTH = 320;
    private static final int HEIGHT = 180;

    @BeforeEach
    void setUp() {
        RendererRequirement.enforce();
    }

    private static List<Series> latency() {
        return List.of(Series.of("p99", 128, 131, 126, 149, 142, 138, 133));
    }

    private static Attributes id() {
        return new Attributes("plot", Set.of(), "plot");
    }

    private static WidgetRenderer renderer() {
        return new WidgetRenderer(
                List.of(
                        Controls.baseStylesheet(),
                        Theme.NORD_DARK.load(),
                        Stylesheet.parse(CascadeLayer.APPLICATION, """
                                #frame { padding: 12px; background: var(--gb-bg) }
                                #plot  { width: 296px; height: 156px }
                                """)),
                TestFont.get());
    }

    private static int[] pixels(Widget chart) {
        var render = renderer();
        var tree = new ElementTree(new Column(List.of(chart), new Attributes("frame", Set.of(), "frame")));
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
    @DisplayName("a threshold above the data stretches the axis to reach it")
    void aLimitYouHaveNotCrossedIsStillOnScreen() {
        // The one that matters most: "we are a long way from the limit" is a
        // reading, and a threshold that only appeared once it had been breached
        // would be a warning light that comes on after the fire.
        var plain = pixels(new LineChart(latency(), List.of(), id()));
        var limited =
                pixels(new LineChart(latency(), List.of(), id()).threshold(Threshold.at(400, Threshold.Level.DANGER)));

        assertFalse(java.util.Arrays.equals(plain, limited), "the chart rescaled to reach 400");
    }

    @Test
    @DisplayName("a line and a band are different drawings of the same limit")
    void linesAndRegions() {
        var line =
                pixels(new LineChart(latency(), List.of(), id()).threshold(Threshold.at(145, Threshold.Level.WARNING)));
        var band = pixels(
                new LineChart(latency(), List.of(), id()).threshold(Threshold.above(145, Threshold.Level.WARNING)));

        assertFalse(java.util.Arrays.equals(line, band));
    }

    @Test
    @DisplayName("a band lets the data through it")
    void aWarningMustNotHideWhatItWarnsAbout() {
        var band = pixels(
                new LineChart(latency(), List.of(), id()).threshold(Threshold.above(120, Threshold.Level.DANGER)));

        // The band covers the whole plot, and the series is still drawn on top of
        // it -- so the chart must still contain the series' own hue. A threshold
        // painted over the data would be a warning that costs you the reading.
        var green = 0;
        for (var pixel : band) {
            var red = (pixel >> 16) & 0xFF;
            var value = (pixel >> 8) & 0xFF;
            var blue = pixel & 0xFF;
            if (value > red + 24 && value > blue + 24) {
                green++;
            }
        }
        assertTrue(green > 100, "expected the series to be visible through the band, got " + green);
    }

    @Test
    @DisplayName("a threshold takes a semantic hue and never a series slot")
    void notASeriesColour() {
        // The palette's job is to keep the things being compared apart; a limit
        // is a statement *about* them. A threshold in slot 4 would look like a
        // fourth series and steal the real one's hue.
        for (var level : Threshold.Level.values()) {
            assertTrue(level.token().startsWith("--gb-"), level.token());
            assertTrue(
                    level.token().endsWith("-line"), "the rank that exists for a stroke on the page: " + level.token());
        }
        assertEquals("--gb-danger-line", Threshold.Level.DANGER.token());
    }

    @Test
    @DisplayName("a region is ordered however it was written")
    void fromAndToAreASet() {
        assertEquals(Threshold.band(90, 10, Threshold.Level.INFO), Threshold.band(10, 90, Threshold.Level.INFO));
        assertTrue(Threshold.at(5, Threshold.Level.INFO).isLine());
        assertFalse(Threshold.above(5, Threshold.Level.INFO).isLine());
    }

    @Test
    @DisplayName("an unbounded side asks nothing of the axis")
    void infinityIsNotADomain() {
        var above = Threshold.above(90, Threshold.Level.DANGER);

        // `above(90)` says the axis must reach 90, and says nothing at all about
        // infinity.
        assertEquals(90.0, above.domainMin());
        assertEquals(
                Double.NEGATIVE_INFINITY,
                above.domainMax(),
                "which is the identity for a max, so it contributes nothing");
    }

    @Test
    @DisplayName("NaN is where a missing value goes, not a limit")
    void aThresholdNeedsAPosition() {
        var failure =
                assertThrows(IllegalArgumentException.class, () -> Threshold.at(Double.NaN, Threshold.Level.DANGER));
        assertTrue(failure.getMessage().contains("NullPolicy"), failure.getMessage());
    }

    @Test
    @DisplayName("thresholds accumulate, so several limits are several calls")
    void severalLimits() {
        var chart = new LineChart(latency(), List.of(), id())
                .threshold(Threshold.at(140, Threshold.Level.WARNING))
                .threshold(Threshold.above(160, Threshold.Level.DANGER));

        assertEquals(2, chart.options().thresholds().size());
        assertEquals(
                List.of(), chart.thresholds(List.of()).options().thresholds(), "and replacing them is the other call");
    }

    @Test
    @DisplayName("a band is drawn in its hue and not in a grey that used to be one")
    void theHueHasToBePerceptible() {
        // Found by looking at the first one: this theme's warning hue at 16% over
        // the dark surface computes to (76, 76, 76), which is *exactly* neutral
        // grey. A band whose semantic colour a reader cannot perceive says
        // "something" rather than "warning", so a band is a wash **and its
        // edges**, and the edges are where the hue lives.
        var band = pixels(
                new LineChart(latency(), List.of(), id()).threshold(Threshold.band(130, 145, Threshold.Level.WARNING)));

        // Yellow is R > G > B; the theme's warning is `#ebcb8b`, and a 1px edge at
        // a fractional y antialiases to about `#cfb582`, so this asks for the
        // *hue* rather than for an exact value. The series' green fails it (its G
        // is the largest channel), so what is being counted is the band.
        var warm = 0;
        var grey = 0;
        for (var pixel : band) {
            var red = (pixel >> 16) & 0xFF;
            var green = (pixel >> 8) & 0xFF;
            var blue = pixel & 0xFF;
            if (red > green && green > blue && red - blue > 40) {
                warm++;
            }
            if (Math.abs(red - blue) <= 4 && red > 70 && red < 90) {
                grey++;
            }
        }
        assertTrue(warm > 100, "expected the band's edges in the warning hue, found " + warm);
        // And the finding itself, kept: the wash *is* that grey, so a band that
        // was only a wash would have been the first number and not the second.
        assertTrue(grey > 1000, "the wash is still a wash, and it is grey: " + grey);
    }

    @Test
    @DisplayName("a warning band, a danger line and its label")
    void golden() {
        var render = renderer();
        var tree = new ElementTree(new Column(
                List.of(new LineChart(latency(), List.of(), id())
                        .threshold(Threshold.band(130, 145, Threshold.Level.WARNING))
                        .threshold(Threshold.at(150, Threshold.Level.DANGER).labelled("SLO"))),
                new Attributes("frame", Set.of(), "frame")));

        GoldenImage.assertMatches(
                "line-chart-threshold-dark",
                WIDTH,
                HEIGHT,
                1.0f,
                frame -> BoxPainter.paint(frame, render.render(tree)));
    }
}
