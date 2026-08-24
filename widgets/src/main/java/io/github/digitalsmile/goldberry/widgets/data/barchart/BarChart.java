package io.github.digitalsmile.goldberry.widgets.data.barchart;

import io.github.digitalsmile.goldberry.css.ComputedStyle;
import io.github.digitalsmile.goldberry.kdl.KdlNode;
import io.github.digitalsmile.goldberry.paint.Box;
import io.github.digitalsmile.goldberry.widget.attr.Attributed;
import io.github.digitalsmile.goldberry.widget.attr.Attributes;
import io.github.digitalsmile.goldberry.widget.style.Paints;
import io.github.digitalsmile.goldberry.widget.style.Styled;
import io.github.digitalsmile.goldberry.widget.Widget;
import io.github.digitalsmile.goldberry.widgets.data.ChartParts;
import io.github.digitalsmile.goldberry.widgets.data.Series;
import io.github.digitalsmile.goldberry.widgets.markup.Markup;
import io.github.digitalsmile.goldberry.widgets.markup.Wiring;
import java.util.List;
import java.util.Set;

/// Magnitude by category — `docs/core-widgets.md` §11's `bar-chart`.
///
/// ```kdl
/// bar-chart {
///     series name="downloads" { point "0.1" 1200; point "0.2" 3400 }
/// }
/// ```
///
/// **Grouped, not stacked.** Two bars side by side answer "which is bigger", and
/// that is the question a bar chart is read for; stacked bars answer "what is
/// the total made of", which is `area-chart`'s job here. Doing both would mean a
/// flag whose two settings are two different charts.
///
/// The y axis **includes zero** and there is no way to ask it not to. A bar
/// encodes its value as a *length*, so a baseline at 90 makes a 3% difference
/// look like a doubling — the most common way a chart lies, and the reason
/// `line-chart` is the only one of the five that may zoom its baseline.
///
/// A negative value hangs below the zero line rather than being drawn upside
/// down, which is the one thing every naive bar renderer gets wrong.
@Markup("bar-chart")
public record BarChart(List<Series> series, List<String> categories,
        io.github.digitalsmile.goldberry.widgets.data.ChartOptions options,
        Attributes attributes)
        implements Widget.Stateful, io.github.digitalsmile.goldberry.widgets.data.ChartSpec,
                Attributed<BarChart> {

    public BarChart {
        series = List.copyOf(series == null ? List.of() : series);
        categories = List.copyOf(categories == null ? List.of() : categories);
        attributes = attributes == null ? Attributes.NONE : attributes;
        options = options == null
                ? io.github.digitalsmile.goldberry.widgets.data.ChartOptions.DEFAULTS : options;
    }

    /// The ordinary form: a chart that has its data.
    public BarChart(List<Series> series, List<String> categories, Attributes attributes) {
        this(series, categories,
                io.github.digitalsmile.goldberry.widgets.data.ChartOptions.DEFAULTS, attributes);
    }

    /// This chart with an axis that reaches **at least** `min…max`, and further
    /// if the data does.
    ///
    /// What stops a flat series rendering as noise: an uptime between 99.91 and
    /// 99.99 auto-scaled is a mountain range made of eight hundredths of a
    /// percent, and `softAxis(99, 100)` draws it as the flat line near the top
    /// that it is — while still showing an outage, because a reading of 40 pushes
    /// the axis down to meet it
    /// ([io.github.digitalsmile.goldberry.widgets.data.Bounds]).
    public BarChart softAxis(double min, double max) {
        return options(options.bounds(
                io.github.digitalsmile.goldberry.widgets.data.Bounds.soft(min, max)));
    }

    /// This chart with an axis that is **exactly** `min…max`, whatever the data
    /// does.
    ///
    /// For a range that is a definition rather than an observation — a percentage
    /// of a whole, a gauge with a physical stop. Data outside it is drawn outside
    /// the plot and clipped, which is the correct rendering of a promise that was
    /// wrong.
    public BarChart axis(double min, double max) {
        return options(options.bounds(
                io.github.digitalsmile.goldberry.widgets.data.Bounds.hard(min, max)));
    }

    /// This chart sharing its crosshair with every other chart in `group`.
    ///
    /// Pointing at Tuesday here puts the crosshair on Tuesday on all of them,
    /// which is how a reader asks what the other panel was doing at the same
    /// moment. Only the chart under the pointer draws the readout
    /// ([io.github.digitalsmile.goldberry.widgets.data.CrosshairGroup]).
    public BarChart crosshair(
            io.github.digitalsmile.goldberry.widgets.data.CrosshairGroup group) {

        return options(options.crosshair(group));
    }

    /// This chart with a different marker rule — a dot at each reading, or not.
    public BarChart markers(io.github.digitalsmile.goldberry.widgets.data.Markers value) {
        return options(options.markers(value));
    }

    /// This chart with a **logarithmic** value axis.
    ///
    /// For a series that spends its life at 3 and spikes to 30 000: on a linear
    /// axis every reading anybody cares about is in the bottom pixel. A log axis
    /// gives each decade the same room.
    ///
    /// **It costs the zeroes.** `log10(0)` is negative infinity, so a
    /// non-positive reading has no position and becomes a hole — the line breaks
    /// there rather than sliding off the bottom
    /// ([ADR-0205](../../../../../../../../book/src/adr/0205-a-log-axis-has-no-room-for-zero.md)).
    /// Only `line-chart` draws one: a bar and a band are lengths from zero, and
    /// zero is not on the axis.
    public BarChart logY() {
        return options(options.logY(true));
    }

    /// This chart with a different interpolation — how the line gets from one
    /// point to the next.
    ///
    /// The default is [io.github.digitalsmile.goldberry.widgets.data.Curve#LINEAR],
    /// which makes the weakest claim about what happened in between. A
    /// `bar-chart` ignores it: a bar is a length rather than a path.
    public BarChart curve(io.github.digitalsmile.goldberry.widgets.data.Curve value) {
        return options(options.curve(value));
    }

    /// This chart with `value` as everything that is not its numbers.
    public BarChart options(io.github.digitalsmile.goldberry.widgets.data.ChartOptions value) {
        return new BarChart(series, categories, value, attributes);
    }

    /// This chart with a **time axis**: one instant per point, so the x is when
    /// rather than which.
    ///
    /// A gap in the sampling becomes a gap on the axis, and the labels step
    /// across second, minute, hour, day, month and year boundaries
    /// ([io.github.digitalsmile.goldberry.widgets.data.TimeAxis]).
    public BarChart times(List<java.time.Instant> value) {
        return options(options.time(
                io.github.digitalsmile.goldberry.widgets.data.TimeAxis.of(value)));
    }

    /// The same, in a zone the application chooses — a server's clock, or `UTC`
    /// for a test.
    public BarChart times(List<java.time.Instant> value, java.time.ZoneId zone) {
        return options(options.time(
                io.github.digitalsmile.goldberry.widgets.data.TimeAxis.of(value).in(zone)));
    }

    /// This chart with `limit` drawn across it — a line or a shaded region, in
    /// one of the four semantic hues.
    ///
    /// Additive, so several limits read as several calls:
    /// `chart.threshold(warn).threshold(fail)`. A threshold is part of the
    /// domain, so one you have not crossed yet is still on screen
    /// ([io.github.digitalsmile.goldberry.widgets.data.Threshold]).
    public BarChart threshold(io.github.digitalsmile.goldberry.widgets.data.Threshold limit) {
        return options(options.threshold(limit));
    }

    /// This chart with exactly these limits, replacing whatever it had.
    public BarChart thresholds(
            List<io.github.digitalsmile.goldberry.widgets.data.Threshold> limits) {

        return options(options.thresholds(limits));
    }

    /// This chart, told what to do where a series has no value.
    ///
    /// The default is [io.github.digitalsmile.goldberry.widgets.data.NullPolicy#GAP],
    /// which is the only one of the three that invents nothing.
    public BarChart nulls(io.github.digitalsmile.goldberry.widgets.data.NullPolicy value) {
        return options(options.nulls(value));
    }

    /// This chart, waiting for its data — it keeps its box and says so.
    ///
    /// The box is the point: a panel whose charts vanished while their queries
    /// resolved would reflow twice per chart (ChartStatus).
    public BarChart loading() {
        return status(io.github.digitalsmile.goldberry.widgets.data.ChartStatus.loading());
    }

    /// This chart, in the application's own words about why there is nothing.
    public BarChart failed(String message) {
        return status(io.github.digitalsmile.goldberry.widgets.data.ChartStatus.failed(message));
    }

    /// This chart with `value` as its state — see
    /// [io.github.digitalsmile.goldberry.widgets.data.ChartStatus].
    public BarChart status(io.github.digitalsmile.goldberry.widgets.data.ChartStatus value) {
        return options(options.status(value));
    }

    public BarChart(List<Series> series) {
        this(series, List.of(), Attributes.NONE);
    }

    /// This chart with a label under each group.
    public BarChart categories(List<String> values) {
        return new BarChart(series, values, options, attributes);
    }

    /// The type of the box this chart's view draws — see
    /// [io.github.digitalsmile.goldberry.widgets.data.ChartSpec#chartType()].
    @Override
    public String chartType() {
        return "bar-chart";
    }

    @Override
    public io.github.digitalsmile.goldberry.widgets.data.ChartParts.Mode mode() {
        return io.github.digitalsmile.goldberry.widgets.data.ChartParts.Mode.BAR;
    }

    /// The state both halves of this chart read — which series is isolated.
    @Override
    public io.github.digitalsmile.goldberry.widget.State<?> createState() {
        return io.github.digitalsmile.goldberry.widgets.data.ChartParts.state();
    }

    @Override
    public Object key() {
        return attributes.key();
    }

    @Override
    public BarChart withAttributes(Attributes value) {
        return new BarChart(series, categories, options, value);
    }

    /// Builds a `bar-chart` from markup — §3.2's inline form.
    public static Widget inflate(KdlNode node, List<Widget> children, Wiring wiring) {
        var read = ChartParts.read(children);
        return new BarChart(read.series(), read.categories(), Attributes.of(node));
    }
}
