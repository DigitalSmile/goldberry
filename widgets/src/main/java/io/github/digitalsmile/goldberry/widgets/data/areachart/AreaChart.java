package io.github.digitalsmile.goldberry.widgets.data.areachart;

import java.util.List;

import io.github.digitalsmile.goldberry.kdl.KdlNode;
import io.github.digitalsmile.goldberry.widget.Widget;
import io.github.digitalsmile.goldberry.widget.attr.Attributed;
import io.github.digitalsmile.goldberry.widget.attr.Attributes;
import io.github.digitalsmile.goldberry.widgets.data.ChartParts;
import io.github.digitalsmile.goldberry.widgets.data.Series;
import io.github.digitalsmile.goldberry.widgets.markup.Markup;
import io.github.digitalsmile.goldberry.widgets.markup.Wiring;

/// A total, and what it is made of — `docs/core-widgets.md` §11's `area-chart`.
///
/// ```kdl
/// area-chart {
///     series name="cache" { point "Mon" 40; point "Tue" 52 }
///     series name="origin" { point "Mon" 12; point "Tue" 9 }
/// }
/// ```
///
/// **Stacked, always.** Overlapping translucent areas are the classic unreadable
/// chart: three series make seven possible colours on screen and none of them is
/// in the legend. Stacked, the bands add to the total — which is what a reader
/// assumes an area chart means anyway, and is the only question this form answers
/// better than a `line-chart` does.
///
/// So the choice between the two is a real one and worth stating: **`line-chart`
/// when the series are separate quantities**, because a stack of unrelated
/// numbers has a meaningless total; **`area-chart` when they are parts of one**.
/// A single series is legitimate in either and reads as a filled line here.
///
/// The y axis **includes zero**, and cannot be talked out of it: a band's meaning
/// is its area, and an area measured from a baseline nobody stated is a
/// proportion that lies.
@Markup("area-chart")
public record AreaChart(
        List<Series> series,
        List<String> categories,
        io.github.digitalsmile.goldberry.widgets.data.ChartOptions options,
        Attributes attributes)
        implements Widget.Stateful, io.github.digitalsmile.goldberry.widgets.data.ChartSpec, Attributed<AreaChart> {

    public AreaChart {
        series = List.copyOf(series == null ? List.of() : series);
        categories = List.copyOf(categories == null ? List.of() : categories);
        attributes = attributes == null ? Attributes.NONE : attributes;
        options = options == null ? io.github.digitalsmile.goldberry.widgets.data.ChartOptions.DEFAULTS : options;
    }

    /// The ordinary form: a chart that has its data.
    public AreaChart(List<Series> series, List<String> categories, Attributes attributes) {
        this(series, categories, io.github.digitalsmile.goldberry.widgets.data.ChartOptions.DEFAULTS, attributes);
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
    public AreaChart softAxis(double min, double max) {
        return options(options.bounds(io.github.digitalsmile.goldberry.widgets.data.Bounds.soft(min, max)));
    }

    /// This chart with an axis that is **exactly** `min…max`, whatever the data
    /// does.
    ///
    /// For a range that is a definition rather than an observation — a percentage
    /// of a whole, a gauge with a physical stop. Data outside it is drawn outside
    /// the plot and clipped, which is the correct rendering of a promise that was
    /// wrong.
    public AreaChart axis(double min, double max) {
        return options(options.bounds(io.github.digitalsmile.goldberry.widgets.data.Bounds.hard(min, max)));
    }

    /// This chart sharing its crosshair with every other chart in `group`.
    ///
    /// Pointing at Tuesday here puts the crosshair on Tuesday on all of them,
    /// which is how a reader asks what the other panel was doing at the same
    /// moment. Only the chart under the pointer draws the readout
    /// ([io.github.digitalsmile.goldberry.widgets.data.CrosshairGroup]).
    public AreaChart crosshair(io.github.digitalsmile.goldberry.widgets.data.CrosshairGroup group) {

        return options(options.crosshair(group));
    }

    /// This chart with a different marker rule — a dot at each reading, or not.
    public AreaChart markers(io.github.digitalsmile.goldberry.widgets.data.Markers value) {
        return options(options.markers(value));
    }

    /// This chart with a different fill in its bands.
    ///
    /// A flat wash by default, which is the honest rendering of a stack: the
    /// bands are adjacent rather than overlapping, they add up to the total, and
    /// a reader compares their thicknesses.
    /// [io.github.digitalsmile.goldberry.widgets.data.Fill#GRADIENT] fades each
    /// band **within its own extent** rather than across the plot, so a thin
    /// band under a thick one is still there to be read
    /// ([ADR-0207](../../../../../../../../book/src/adr/0207-a-fill-may-be-a-ramp.md)).
    ///
    /// [io.github.digitalsmile.goldberry.widgets.data.Fill#NONE] is read as a
    /// flat wash: a band with no fill is not a band.
    public AreaChart fill(io.github.digitalsmile.goldberry.widgets.data.Fill value) {
        return options(options.fill(value));
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
    public AreaChart logY() {
        return options(options.logY(true));
    }

    /// This chart with a different interpolation — how the line gets from one
    /// point to the next.
    ///
    /// The default is [io.github.digitalsmile.goldberry.widgets.data.Curve#LINEAR],
    /// which makes the weakest claim about what happened in between. A
    /// `bar-chart` ignores it: a bar is a length rather than a path.
    public AreaChart curve(io.github.digitalsmile.goldberry.widgets.data.Curve value) {
        return options(options.curve(value));
    }

    /// This chart with `value` as everything that is not its numbers.
    public AreaChart options(io.github.digitalsmile.goldberry.widgets.data.ChartOptions value) {
        return new AreaChart(series, categories, value, attributes);
    }

    /// This chart with a **time axis**: one instant per point, so the x is when
    /// rather than which.
    ///
    /// A gap in the sampling becomes a gap on the axis, and the labels step
    /// across second, minute, hour, day, month and year boundaries
    /// ([io.github.digitalsmile.goldberry.widgets.data.TimeAxis]).
    public AreaChart times(List<java.time.Instant> value) {
        return options(options.time(io.github.digitalsmile.goldberry.widgets.data.TimeAxis.of(value)));
    }

    /// The same, in a zone the application chooses — a server's clock, or `UTC`
    /// for a test.
    public AreaChart times(List<java.time.Instant> value, java.time.ZoneId zone) {
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
    public AreaChart threshold(io.github.digitalsmile.goldberry.widgets.data.Threshold limit) {
        return options(options.threshold(limit));
    }

    /// This chart with exactly these limits, replacing whatever it had.
    public AreaChart thresholds(List<io.github.digitalsmile.goldberry.widgets.data.Threshold> limits) {

        return options(options.thresholds(limits));
    }

    /// This chart, told what to do where a series has no value.
    ///
    /// The default is [io.github.digitalsmile.goldberry.widgets.data.NullPolicy#GAP],
    /// which is the only one of the three that invents nothing.
    public AreaChart nulls(io.github.digitalsmile.goldberry.widgets.data.NullPolicy value) {
        return options(options.nulls(value));
    }

    /// This chart, waiting for its data — it keeps its box and says so.
    ///
    /// The box is the point: a panel whose charts vanished while their queries
    /// resolved would reflow twice per chart (ChartStatus).
    public AreaChart loading() {
        return status(io.github.digitalsmile.goldberry.widgets.data.ChartStatus.loading());
    }

    /// This chart, in the application's own words about why there is nothing.
    public AreaChart failed(String message) {
        return status(io.github.digitalsmile.goldberry.widgets.data.ChartStatus.failed(message));
    }

    /// This chart with `value` as its state — see
    /// [io.github.digitalsmile.goldberry.widgets.data.ChartStatus].
    public AreaChart status(io.github.digitalsmile.goldberry.widgets.data.ChartStatus value) {
        return options(options.status(value));
    }

    public AreaChart(List<Series> series) {
        this(series, List.of(), Attributes.NONE);
    }

    /// This chart with a label under each point.
    public AreaChart categories(List<String> values) {
        return new AreaChart(series, values, options, attributes);
    }

    /// The type of the box this chart's view draws — see
    /// [io.github.digitalsmile.goldberry.widgets.data.ChartSpec#chartType()].
    @Override
    public String chartType() {
        return "area-chart";
    }

    @Override
    public io.github.digitalsmile.goldberry.widgets.data.ChartParts.Mode mode() {
        return io.github.digitalsmile.goldberry.widgets.data.ChartParts.Mode.AREA;
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
    public AreaChart withAttributes(Attributes value) {
        return new AreaChart(series, categories, options, value);
    }

    /// Builds an `area-chart` from markup — §3.2's inline form.
    public static Widget inflate(KdlNode node, List<Widget> children, Wiring wiring) {
        var read = ChartParts.read(children);
        return new AreaChart(read.series(), read.categories(), Attributes.of(node));
    }
}
