package io.github.digitalsmile.goldberry.widgets.data;

import static io.github.digitalsmile.goldberry.widgets.TestAttributes.id;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
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
import io.github.digitalsmile.goldberry.widget.ElementTree;
import io.github.digitalsmile.goldberry.widget.Widget;
import io.github.digitalsmile.goldberry.widget.WidgetRenderer;
import io.github.digitalsmile.goldberry.widgets.Controls;
import io.github.digitalsmile.goldberry.widgets.Widgets;
import io.github.digitalsmile.goldberry.widgets.controls.TestFont;
import io.github.digitalsmile.goldberry.widgets.core.Column;
import io.github.digitalsmile.goldberry.widgets.data.areachart.AreaChart;
import io.github.digitalsmile.goldberry.widgets.data.barchart.BarChart;

/// §11's other two axis charts, and the rules they do not share with
/// `line-chart`.
class ChartsGoldenTest {

    @BeforeEach
    void setUp() {
        RendererRequirement.enforce();
    }

    private static List<Series> two() {
        return List.of(Series.of("Cache", 40, 52, 44, 61, 58), Series.of("Origin", 12, 9, 15, 11, 14));
    }

    private static final List<String> DAYS = List.of("Mon", "Tue", "Wed", "Thu", "Fri");

    private void paint(String name, Widget chart) {
        var sheet = Stylesheet.parse(CascadeLayer.APPLICATION, """
                #frame { padding: 12px; background: var(--gb-bg) }
                #plot  { width: 296px; height: 156px }
                """);
        var tree = new ElementTree(new Column(List.of(chart), id("frame")));
        var renderer =
                new WidgetRenderer(List.of(Controls.baseStylesheet(), Theme.NORD_DARK.load(), sheet), TestFont.get());

        GoldenImage.assertMatches(name, 320, 180, 1.0f, frame -> BoxPainter.paint(frame, renderer.render(tree)));
    }

    @Test
    @DisplayName("a stacked area chart")
    void areaGolden() {
        paint("area-chart-dark", new AreaChart(two(), DAYS, id("plot")));
    }

    @Test
    @DisplayName("a grouped bar chart")
    void barGolden() {
        paint("bar-chart-dark", new BarChart(two(), DAYS, id("plot")));
    }

    @Test
    @DisplayName("a bar chart's axis includes zero however far the data is from it")
    void barsStartAtZero() {
        // The most common way a chart lies: a baseline at 90 makes a 3%
        // difference look like a doubling. A bar encodes its value as a length,
        // so the length has to start where the value does.
        var chart = new BarChart(List.of(Series.of("Uptime", 99.1, 99.4, 99.2)));

        // Rendered, because the domain is decided in `render` where the cascade
        // is -- reading it any other way would be reading a different number.
        var renderer = new WidgetRenderer(List.of(Controls.baseStylesheet(), Theme.NORD_DARK.load()), TestFont.get());
        renderer.render(new ElementTree(chart));

        var labelling = Ticks.extended(0, 99.4, 5);
        assertEquals(0.0, labelling.min(), "the axis this data gets must start at zero");
        assertEquals(ChartParts.Mode.BAR, chart.mode(), "and it is the mode that decides it, for every bar chart");
    }

    @Test
    @DisplayName("both read §3.2's inline data")
    void inflateFromKdl() {
        var area = Widgets.inflater().inflate(KdlParser.parse("""
                area-chart {
                    series name="cache" { point "Mon" 40; point "Tue" 52 }
                    series name="origin" { point "Mon" 12; point "Tue" 9 }
                }
                """).getFirst());
        var bar = Widgets.inflater().inflate(KdlParser.parse("""
                bar-chart {
                    series name="downloads" { point "0.1" 1200; point "0.2" 3400 }
                }
                """).getFirst());

        assertEquals(2, ((AreaChart) area).series().size());
        assertEquals(List.of("Mon", "Tue"), ((AreaChart) area).categories());
        assertEquals(
                List.of(1200.0, 3400.0), ((BarChart) bar).series().getFirst().values());
    }

    @Test
    @DisplayName("a donut of three slices")
    void donutGolden() {
        paint(
                "donut-chart-dark",
                new io.github.digitalsmile.goldberry.widgets.data.donutchart.DonutChart(
                        List.of(Series.of("Cache", 62), Series.of("Origin", 24), Series.of("Miss", 14)), id("plot")));
    }

    @Test
    @DisplayName("a donut refuses two slices and refuses nine")
    void aDonutKnowsWhatItIsNotFor() {
        // Refused at construction, which is where `dialog` refuses two
        // affirmative buttons and for the same reason: a widget that cannot mean
        // anything sensible should say so where the mistake is.
        var two = List.of(Series.of("Used", 62), Series.of("Free", 38));
        var nine = new java.util.ArrayList<Series>();
        for (var i = 0; i < 9; i++) {
            nine.add(Series.of("s" + i, 1));
        }

        var few = assertThrows(
                IllegalArgumentException.class,
                () -> new io.github.digitalsmile.goldberry.widgets.data.donutchart.DonutChart(two));
        var many = assertThrows(
                IllegalArgumentException.class,
                () -> new io.github.digitalsmile.goldberry.widgets.data.donutchart.DonutChart(nine));

        assertTrue(few.getMessage().contains("progress"), few.getMessage());
        assertTrue(many.getMessage().contains("bar-chart"), many.getMessage());
    }

    @Test
    @DisplayName("a donut always has a legend, unlike the axis charts")
    void aDonutAlwaysNamesItsSlices() {
        // An axis chart with one series is named by its title. A donut's slices
        // are never one thing and an arc has nowhere to write a name, so without
        // a legend the chart is a set of coloured shapes.
        var donut = new io.github.digitalsmile.goldberry.widgets.data.donutchart.DonutChart(
                List.of(Series.of("Cache", 62), Series.of("Origin", 24), Series.of("Miss", 14)));

        assertEquals(2, donut.children().size());
        assertTrue(
                donut.children().getLast()
                        instanceof io.github.digitalsmile.goldberry.widgets.data.linechart.ChartLegend);
    }

    @Test
    @DisplayName("all three axis charts share one legend rule")
    void oneLegendRule() {
        // Counted in the **boxes**, because a chart is a stateful widget now and
        // its parts are its state's rather than its own -- and because what the
        // rule is about is whether a legend is on the screen.
        var one = List.of(Series.of("Only", 1, 2));
        assertEquals(1, partsOf(new AreaChart(one)), "one series: the plot, and no legend");
        assertEquals(1, partsOf(new BarChart(one)));
        assertEquals(2, partsOf(new AreaChart(two())), "two series: a legend as well");
        assertEquals(2, partsOf(new BarChart(two())));
        assertEquals(
                2,
                partsOf(new io.github.digitalsmile.goldberry.widgets.data.linechart.LineChart(two())),
                "and the third chart obeys the same one");
    }

    /// How many parts `chart` painted — one for the plot, two with a legend.
    private static int partsOf(Widget chart) {
        var renderer = new WidgetRenderer(List.of(Controls.baseStylesheet(), Theme.NORD_DARK.load()), TestFont.get());
        return renderer.render(new ElementTree(chart)).children().size();
    }
}
