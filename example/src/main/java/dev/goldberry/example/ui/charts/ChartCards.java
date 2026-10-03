package dev.goldberry.example.ui.charts;

import java.time.ZoneOffset;
import java.util.List;

import dev.goldberry.example.docs.DocLink;
import dev.goldberry.example.ui.gallery.ShowcaseCard;
import dev.goldberry.widget.Widget;
import dev.goldberry.widget.attr.Attributes;
import dev.goldberry.widgets.core.Row;
import dev.goldberry.widgets.data.Curve;
import dev.goldberry.widgets.data.Fill;
import dev.goldberry.widgets.data.NullPolicy;
import dev.goldberry.widgets.data.Series;
import dev.goldberry.widgets.data.Threshold;
import dev.goldberry.widgets.data.areachart.AreaChart;
import dev.goldberry.widgets.data.barchart.BarChart;
import dev.goldberry.widgets.data.donutchart.DonutChart;
import dev.goldberry.widgets.data.linechart.LineChart;
import dev.goldberry.widgets.data.sparkline.Sparkline;
import dev.goldberry.widgets.panel.statistic.Statistic;
import dev.goldberry.widgets.text.Text;

/// The Charts screen's cards that hold no state: one per chart, and the line
/// chart's knobs on cards of their own.
///
/// Read more: [Charts](https://goldberry.dev/docs/components/charts.html).
final class ChartCards {

    private static final String CHAPTER = "components/charts";

    private ChartCards() {}

    /// A time axis, a limit and a fade under the line, on one chart.
    ///
    /// The card's stylesheet rule recolours the series through `--gb-chart-1`, and
    /// the line and its legend swatch both follow, because a custom property
    /// inherits.
    static Widget watch() {
        return new ShowcaseCard(
                        "watch-card",
                        "A time axis, a limit and a fill",
                        "The x of this line is when: the ninth reading is three hours after the eighth, and the"
                                + " axis shows it. The shaded band is a threshold in a semantic colour, and the"
                                + " fill fades from the line toward the axis.",
                        DocLink.to(CHAPTER, "line-chart"))
                .of(new LineChart(List.of(new Series("Watch", March.WATCH)), List.of(), Attributes.NONE.id("watch"))
                        .times(March.NIGHTS, ZoneOffset.UTC)
                        .threshold(Threshold.above(145, Threshold.Level.WARNING).labelled("Too long alone"))
                        .fill(Fill.GRADIENT));
    }

    /// The same twelve readings, three of them missing, under the two null
    /// policies that disagree about what a missing one means.
    ///
    /// One card and not two, because the wrong picture is only obviously wrong
    /// beside the right one.
    static Widget dropouts() {
        var beacons = List.of(new Series("Beacons", March.BEACONS));
        return new ShowcaseCard(
                        "dropouts-card",
                        "A hole is not a zero",
                        "A missing reading is a hole. The default policy leaves a gap, and a lone reading between"
                                + " two holes is a dot. ZERO draws the same numbers diving to the baseline.",
                        DocLink.to(CHAPTER, "line-chart"))
                .of(
                        new LineChart(beacons, List.of(), Attributes.NONE.id("beacons")),
                        caption("Gap, the default."),
                        new LineChart(beacons, List.of(), Attributes.NONE.id("beacons-zero")).nulls(NullPolicy.ZERO),
                        caption("Zero, claiming the beacons went out."));
    }

    /// Two series of bars, grouped by day.
    static Widget sightings() {
        return new ShowcaseCard(
                        "sightings-card",
                        "Bars by category",
                        "One group of bars per label, side by side and never stacked. The axis always includes zero,"
                                + " because a bar's length is its value. Hover a day to highlight its band.",
                        DocLink.to(CHAPTER, "bar-chart"))
                .of(new BarChart(
                        List.of(Series.of("Crebain", 18, 24, 14, 9, 21), Series.of("Riders", 3, 2, 5, 1, 4)),
                        List.of("Mon", "Tue", "Wed", "Thu", "Fri"),
                        Attributes.NONE.id("sightings")));
    }

    /// Two bands stacked, smoothed on both edges so the stack still nests.
    static Widget provisions() {
        return new ShowcaseCard(
                        "provisions-card",
                        "Stacked bands",
                        "An area chart stacks its series into bands that add up to a total. This one is smooth:"
                                + " the curve is monotone, so it cannot swing below zero, and it is applied to both"
                                + " edges of each band.",
                        DocLink.to(CHAPTER, "area-chart"))
                .of(new AreaChart(
                                List.of(new Series("Lembas", March.LEMBAS), new Series("Dried meat", March.DRIED_MEAT)),
                                March.DAYS,
                                Attributes.NONE.id("provisions"))
                        .curve(Curve.SMOOTH));
    }

    /// Three slices of one ring.
    static Widget packs() {
        return new ShowcaseCard(
                        "packs-card",
                        "Part to whole",
                        "A donut is three to eight slices, one per series, with a legend that is always shown."
                                + " Hover a slice, or Tab to the ring and press the arrows, to read its share.",
                        DocLink.to(CHAPTER, "donut-chart"))
                .of(new DonutChart(
                        List.of(Series.of("Lembas", 62), Series.of("Dried meat", 24), Series.of("Nothing", 14)),
                        Attributes.NONE.id("packs")));
    }

    /// Two statistics, each with a sparkline drawn in its delta's colour.
    static Widget sparklines() {
        return new ShowcaseCard(
                        "safe-card",
                        "A trend beside a number",
                        "A sparkline has no axes and no legend: it is the shape of a change, scaled to its own"
                                + " range. It is drawn in the colour it inherits, so inside a statistic it takes"
                                + " the delta's hue.",
                        DocLink.to(CHAPTER, "sparkline"))
                .of(new Row(
                        List.of(
                                new Statistic(
                                        "Days without loss",
                                        "93",
                                        "d",
                                        "+7",
                                        Statistic.Direction.UP,
                                        new Sparkline(March.SAFE, true, true, Attributes.NONE),
                                        Attributes.NONE.id("safe")),
                                new Statistic(
                                        "Leagues to go",
                                        "1,340",
                                        null,
                                        "-42",
                                        Statistic.Direction.DOWN,
                                        new Sparkline(March.REMAINING, false, true, Attributes.NONE),
                                        Attributes.NONE.id("remaining"))),
                        Attributes.NONE.id("charts-statistics")));
    }

    /// What the five charts leave to something else.
    static Widget notHere() {
        return new ShowcaseCard(
                        "charts-not-here",
                        "What is not a chart here",
                        "No dual y-axis, no colour argument per series, no histogram, heatmap, scatter, pan or"
                                + " zoom. When none of the five fits, the answer is a canvas, a table, or another"
                                + " module.",
                        DocLink.to(CHAPTER, "what-is-not-a-chart-here"))
                .reference();
    }

    private static Widget caption(String text) {
        return new Text(text, Attributes.NONE.classes("caption"));
    }
}
