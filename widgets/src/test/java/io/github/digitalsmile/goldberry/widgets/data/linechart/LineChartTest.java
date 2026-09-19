package io.github.digitalsmile.goldberry.widgets.data.linechart;

import static io.github.digitalsmile.goldberry.widgets.TestAttributes.id;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import io.github.digitalsmile.goldberry.RendererRequirement;
import io.github.digitalsmile.goldberry.css.Stylesheet;
import io.github.digitalsmile.goldberry.css.Theme;
import io.github.digitalsmile.goldberry.css.cascade.CascadeLayer;
import io.github.digitalsmile.goldberry.golden.GoldenImage;
import io.github.digitalsmile.goldberry.kdl.KdlParser;
import io.github.digitalsmile.goldberry.paint.BoxPainter;
import io.github.digitalsmile.goldberry.paint.TestFrames;
import io.github.digitalsmile.goldberry.widget.ElementTree;
import io.github.digitalsmile.goldberry.widget.WidgetRenderer;
import io.github.digitalsmile.goldberry.widgets.Controls;
import io.github.digitalsmile.goldberry.widgets.Widgets;
import io.github.digitalsmile.goldberry.widgets.controls.TestFont;
import io.github.digitalsmile.goldberry.widgets.core.Column;
import io.github.digitalsmile.goldberry.widgets.data.Series;

/// §11's `line-chart`: the first chart with axes, and the first with more than
/// one of anything.
class LineChartTest {

    @BeforeEach
    void setUp() {
        RendererRequirement.enforce();
    }

    private static List<Series> two() {
        return List.of(
                Series.of("Downloads", 12, 19, 15, 27, 31, 28, 36), Series.of("Installs", 8, 11, 9, 18, 21, 19, 24));
    }

    @Test
    @DisplayName("a legend appears for two series and not for one")
    void theLegendIsARuleNotAnOption() {
        // With one line the title names it and a legend box repeats it; with two
        // the colour is the only thing telling them apart, so identity must not
        // be colour alone.
        // Through `ChartParts`, which is where the rule lives and what all four
        // charts call -- a chart itself is a stateful widget now and has no
        // children of its own to count.
        var one = io.github.digitalsmile.goldberry.widgets.data.ChartParts.of(
                List.of(Series.of("Downloads", 1, 2, 3)),
                List.of(),
                io.github.digitalsmile.goldberry.widgets.data.ChartParts.Mode.LINE);
        var two = io.github.digitalsmile.goldberry.widgets.data.ChartParts.of(
                two(), List.of(), io.github.digitalsmile.goldberry.widgets.data.ChartParts.Mode.LINE);

        assertEquals(1, one.size(), "one series: the plot and nothing else");
        assertTrue(one.getFirst() instanceof ChartPlot);
        assertEquals(2, two.size());
        assertTrue(two.getLast() instanceof ChartLegend);
    }

    @Test
    @DisplayName("the legend names every series, in the order their colours were assigned")
    void legendEntriesFollowTheSeriesOrder() {
        var legend = (ChartLegend) io.github.digitalsmile.goldberry.widgets.data.ChartParts.of(
                        two(), List.of(), io.github.digitalsmile.goldberry.widgets.data.ChartParts.Mode.LINE)
                .getLast();

        var entries = legend.children();
        assertEquals(2, entries.size());
        assertEquals("Downloads", ((ChartLegendEntry) entries.getFirst()).name());
        assertEquals(0, ((ChartLegendEntry) entries.getFirst()).slot());
        assertEquals(
                1,
                ((ChartLegendEntry) entries.getLast()).slot(),
                "the second series takes the second slot -- the order is the CVD mechanism");
    }

    @Test
    @DisplayName("the swatch takes the palette and the label does not")
    void onlyTheSwatchIsColoured() {
        // §14's rule about text: a colour beside a word carries identity, and the
        // word stays in the ordinary ink. A legend drawn in the series colour
        // fails a contrast check the moment somebody picks a pale slot.
        var renderer = new WidgetRenderer(List.of(Controls.baseStylesheet(), Theme.NORD_DARK.load()), TestFont.get());
        var box = renderer.render(new ElementTree(new ChartLegendEntry(0, "Downloads")));

        var swatch = box.children().getFirst();
        var label = box.children().getLast();
        assertEquals(0xFF73A340, swatch.background(), "the swatch is series slot 1");
        assertFalse(
                label.text() != null && label.text().argb() == 0xFF73A340,
                "the label is not drawn in the series colour");
    }

    @Test
    @DisplayName("a rule on the chart reaches both the line and its swatch")
    void oneRuleRecoloursBothHalves() {
        // Custom properties inherit, so the token set on the chart reaches the
        // legend entry nested inside it. One rule, both halves (ADR-0195).
        var sheet = Stylesheet.parse(CascadeLayer.APPLICATION, "#plot { --gb-chart-1: #b48ead }");
        var renderer =
                new WidgetRenderer(List.of(Controls.baseStylesheet(), Theme.NORD_DARK.load(), sheet), TestFont.get());

        var box = renderer.render(new ElementTree(new LineChart(two(), List.of(), id("plot"))));

        // chart-legend -> chart-legend-entry -> swatch
        var legend = box.children().getLast();
        var swatch = legend.children().getFirst().children().getFirst();
        assertEquals(0xFFB48EAD, swatch.background(), "the swatch followed the rule");
    }

    @Test
    @DisplayName("markup writes one, taking the x labels from the first series")
    void inflatesFromKdl() {
        var widget = Widgets.inflater().inflate(KdlParser.parse("""
                line-chart {
                    series name="downloads" { point "0.1" 1200; point "0.2" 3400 }
                    series name="installs" { point "0.1" 900; point "0.2" 2100 }
                }
                """).getFirst());

        var chart = (LineChart) widget;
        assertEquals(2, chart.series().size());
        assertEquals("downloads", chart.series().getFirst().name());
        assertEquals(List.of(1200.0, 3400.0), chart.series().getFirst().values());
        // One x axis, so the categories are the first series' point names: a
        // second series naming its points differently would be two x axes.
        assertEquals(List.of("0.1", "0.2"), chart.categories());
    }

    /// The frame `chart` paints, at the size the golden below uses.
    private static int[] pixels(List<Series> series) {
        var sheet = Stylesheet.parse(CascadeLayer.APPLICATION, """
                #frame { padding: 12px; background: var(--gb-bg) }
                #plot  { width: 296px; height: 156px }
                """);
        var renderer =
                new WidgetRenderer(List.of(Controls.baseStylesheet(), Theme.NORD_DARK.load(), sheet), TestFont.get());
        var tree = new ElementTree(new Column(List.of(new LineChart(series, List.of(), id("plot"))), id("frame")));
        var target = TestFrames.of(320, 180, 1.0f);
        try {
            BoxPainter.paint(target.frame(), renderer.render(tree));
        } finally {
            target.end();
        }
        var out = new int[320 * 180];
        for (var y = 0; y < 180; y++) {
            for (var x = 0; x < 320; x++) {
                out[y * 320 + x] = target.pixel(x, y);
            }
        }
        return out;
    }

    @Test
    @DisplayName("a series shorter than the chart stops where it stops")
    void oneScaleForEveryLine() {
        // Each line was scaled to its **own** length, so a series of four
        // readings on a chart whose longest has seven was stretched across the
        // whole plot -- while the crosshair, the markers and the bands all place
        // a point against the longest. There is one x axis, so a series that
        // stops early must draw exactly as one whose remaining readings are
        // missing does.
        var longest = Series.of("Downloads", 12, 19, 15, 27, 31, 28, 36);
        var stops = Series.of("Installs", 8, 11, 9, 18);
        var holed = Series.of("Installs", 8, 11, 9, 18, Double.NaN, Double.NaN, Double.NaN);

        assertArrayEquals(
                pixels(List.of(longest, holed)),
                pixels(List.of(longest, stops)),
                "the short series was drawn somewhere other than under the indices it holds");
    }

    @Test
    @DisplayName("a chart with no series says so rather than drawing an empty grid")
    void emptyIsNotAnError() {
        var renderer = new WidgetRenderer(List.of(Controls.baseStylesheet(), Theme.NORD_DARK.load()), TestFont.get());

        var box = renderer.render(new ElementTree(new LineChart(List.of())));

        // One part, and it is a sentence rather than a plot: gridlines are an
        // assertion about a scale, and there is none (ADR-0200). What it says is
        // `ChartStatusTest`'s.
        assertEquals(1, box.children().size(), "the message, and nothing else");
    }

    @Test
    @DisplayName("two series with axes, gridlines and a legend")
    void golden() {
        var sheet = Stylesheet.parse(CascadeLayer.APPLICATION, """
                #frame { padding: 12px; background: var(--gb-bg) }
                #plot  { width: 296px; height: 156px }
                """);

        var tree = new ElementTree(new Column(
                List.of(new LineChart(two(), List.of("Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun"), id("plot"))),
                id("frame")));
        var renderer =
                new WidgetRenderer(List.of(Controls.baseStylesheet(), Theme.NORD_DARK.load(), sheet), TestFont.get());

        GoldenImage.assertMatches(
                "line-chart-dark", 320, 180, 1.0f, frame -> BoxPainter.paint(frame, renderer.render(tree)));
    }
}
