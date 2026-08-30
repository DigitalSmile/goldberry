package io.github.digitalsmile.goldberry.widgets.data;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
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
import io.github.digitalsmile.goldberry.paint.Box;
import io.github.digitalsmile.goldberry.paint.BoxPainter;
import io.github.digitalsmile.goldberry.widget.Element;
import io.github.digitalsmile.goldberry.widget.ElementTree;
import io.github.digitalsmile.goldberry.widget.Widget;
import io.github.digitalsmile.goldberry.widget.WidgetRenderer;
import io.github.digitalsmile.goldberry.widget.attr.Attributes;
import io.github.digitalsmile.goldberry.widgets.Controls;
import io.github.digitalsmile.goldberry.widgets.controls.TestFont;
import io.github.digitalsmile.goldberry.widgets.core.Column;
import io.github.digitalsmile.goldberry.widgets.data.areachart.AreaChart;
import io.github.digitalsmile.goldberry.widgets.data.barchart.BarChart;
import io.github.digitalsmile.goldberry.widgets.data.donutchart.DonutChart;
import io.github.digitalsmile.goldberry.widgets.data.linechart.LineChart;

/// What a chart draws when it has nothing to draw — `charts.md` §3.1's "a themed
/// message, never an empty grid".
///
/// An empty grid is not a neutral picture. Gridlines and axis labels are an
/// assertion about a scale, so a chart drawing `0, 5, 10, 15, 20` over no data has
/// invented every one of those numbers — which is the same class of untruth as a
/// bar chart with a baseline at 90.
class ChartStatusTest {

    @BeforeEach
    void setUp() {
        RendererRequirement.enforce();
    }

    private static List<Series> two() {
        return List.of(Series.of("Downloads", 12, 19, 15, 27, 31), Series.of("Installs", 8, 11, 9, 18, 21));
    }

    private static WidgetRenderer renderer() {
        return new WidgetRenderer(List.of(Controls.baseStylesheet(), Theme.NORD_DARK.load()), TestFont.get());
    }

    /// Every [ChartMessage] in the tree `chart` renders to, as its text.
    private static List<String> messagesIn(Widget chart) {
        var box = renderer().render(new ElementTree(chart));
        var found = new ArrayList<String>();
        collect(box, found);
        return found;
    }

    private static void collect(Box box, List<String> into) {
        if (box.owner() instanceof Element element && element.widget() instanceof ChartMessage message) {
            into.add(message.text());
        }
        for (var child : box.children()) {
            collect(child, into);
        }
    }

    /// Whether anything in the tree is a plot — the picture, as opposed to the
    /// sentence.
    private static boolean hasPlot(Widget chart) {
        var box = renderer().render(new ElementTree(chart));
        return plotIn(box);
    }

    private static boolean plotIn(Box box) {
        if (box.owner() instanceof Element element
                && element.widget() instanceof io.github.digitalsmile.goldberry.widget.style.Styled styled
                && ("chart-plot".equals(styled.cssType()) || "donut-plot".equals(styled.cssType()))) {
            return true;
        }
        for (var child : box.children()) {
            if (plotIn(child)) {
                return true;
            }
        }
        return false;
    }

    @Test
    @DisplayName("a chart with no series says so instead of drawing axes")
    void emptyIsASentence() {
        assertEquals(List.of(ChartStatus.NO_DATA), messagesIn(new LineChart(List.of())));
        assertTrue(!hasPlot(new LineChart(List.of())), "and there is no grid to assert a scale nobody supplied");
    }

    @Test
    @DisplayName("a chart of named series with no numbers in them is empty too")
    void namedButEmptyIsStillEmpty() {
        // What a query that returned no rows looks like: the names survived and
        // the values did not.
        var named = List.of(new Series("Downloads", List.of()), new Series("Installs", List.of()));

        assertEquals(List.of(ChartStatus.NO_DATA), messagesIn(new LineChart(named)));
    }

    @Test
    @DisplayName("a chart with data draws its data and says nothing")
    void dataIsNotAMessage() {
        assertEquals(List.of(), messagesIn(new LineChart(two())));
        assertTrue(hasPlot(new LineChart(two())));
    }

    @Test
    @DisplayName("loading and failing are the application's to say, and it keeps the box")
    void theOtherTwoStates() {
        assertEquals(
                List.of(ChartStatus.LOADING_MESSAGE),
                messagesIn(new LineChart(two()).loading()),
                "data it already has does not stop it waiting for more");
        assertEquals(
                List.of("Querying Prometheus…"),
                messagesIn(new LineChart(two()).status(ChartStatus.loading("Querying Prometheus…"))));
        assertEquals(List.of("Prometheus timed out"), messagesIn(new LineChart(two()).failed("Prometheus timed out")));
        assertEquals(
                List.of(ChartStatus.FAILED_MESSAGE),
                messagesIn(new LineChart(two()).failed(null)),
                "a chart that failed silently is worse than one that admits it");
    }

    @Test
    @DisplayName("a loading chart is exactly as tall as the chart it is about to be")
    void theBoxDoesNotMove() {
        // The whole reason a chart owns this rather than the application: a
        // `masonry` of cards whose charts vanished while their queries resolved
        // would reflow the wall twice per panel. Measured rather than argued,
        // because it is the one claim the CSS has to keep -- `chart-message` takes
        // the plot's `flex-grow`, so it fills the box the picture would have.
        var sheet = Stylesheet.parse(CascadeLayer.APPLICATION, "#plot { width: 296px; height: 156px }");
        var render =
                new WidgetRenderer(List.of(Controls.baseStylesheet(), Theme.NORD_DARK.load(), sheet), TestFont.get());
        var id = new Attributes("plot", Set.of(), "plot");

        var ready = heightOf(render, new LineChart(two(), List.of(), id));
        var waiting = heightOf(render, new LineChart(two(), List.of(), id).loading());
        var broken = heightOf(render, new LineChart(two(), List.of(), id).failed("nope"));

        assertEquals(156f, ready, "the box the stylesheet gave it");
        assertEquals(ready, waiting, "and the same box while it waits");
        assertEquals(ready, broken, "and the same box when it gave up");
    }

    /// How tall `chart` is laid out, in a 320x180 frame.
    private static float heightOf(WidgetRenderer render, Widget chart) {
        var target = io.github.digitalsmile.goldberry.paint.TestFrames.of(320, 180, 1.0f);
        try {
            var box = render.render(
                    new ElementTree(new Column(List.of(chart), new Attributes("frame", Set.of(), "frame"))));
            var heights = new ArrayList<Float>();
            BoxPainter.forEachBox(target.frame(), box, (each, layout) -> {
                if (each.owner() instanceof Element element && "plot".equals(element.id())) {
                    heights.add(layout.height());
                }
            });
            assertEquals(1, heights.size(), "expected exactly one chart");
            return heights.getFirst();
        } finally {
            target.end();
        }
    }

    @Test
    @DisplayName("all four charts answer the same way")
    void oneRuleForAllOfThem() {
        assertEquals(List.of(ChartStatus.NO_DATA), messagesIn(new AreaChart(List.of())));
        assertEquals(List.of(ChartStatus.NO_DATA), messagesIn(new BarChart(List.of())));
        assertEquals(List.of(ChartStatus.NO_DATA), messagesIn(new DonutChart(List.of())));
        assertEquals(List.of(ChartStatus.LOADING_MESSAGE), messagesIn(new DonutChart(List.of()).loading()));
    }

    @Test
    @DisplayName("a donut of three zeroes has no whole to be part of")
    void aRingOfNothingIsEmpty() {
        // The one place "empty" means something different: a donut asserts that
        // its arcs are shares of something, and three zeroes are three names.
        var zeroes = List.of(Series.of("Cache", 0), Series.of("Origin", 0), Series.of("Miss", 0));

        assertEquals(List.of(ChartStatus.NO_DATA), messagesIn(new DonutChart(zeroes)));
        assertTrue(!hasPlot(new DonutChart(zeroes)));
    }

    @Test
    @DisplayName("a state carries a class a stylesheet can select it by")
    void everyStateIsSelectable() {
        assertEquals("empty", ChartStatus.READY.styleClass(false));
        assertNull(ChartStatus.READY.styleClass(true), "a chart with data is not a state");
        assertEquals("loading", ChartStatus.loading().styleClass(true));
        assertEquals("failed", ChartStatus.failed("nope").styleClass(true));
    }

    @Test
    @DisplayName("a failure, in the danger hue and in the application's words")
    void golden() {
        var sheet = Stylesheet.parse(CascadeLayer.APPLICATION, """
                #frame { padding: 12px; background: var(--gb-bg) }
                #plot  { width: 296px; height: 156px }
                """);
        var tree = new ElementTree(new Column(
                List.of(new LineChart(two(), List.of(), new Attributes("plot", Set.of(), "plot"))
                        .failed("Prometheus timed out")),
                new Attributes("frame", Set.of(), "frame")));
        var render =
                new WidgetRenderer(List.of(Controls.baseStylesheet(), Theme.NORD_DARK.load(), sheet), TestFont.get());

        GoldenImage.assertMatches(
                "line-chart-failed-dark", 320, 180, 1.0f, frame -> BoxPainter.paint(frame, render.render(tree)));
    }
}
