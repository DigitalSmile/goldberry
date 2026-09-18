package io.github.digitalsmile.goldberry.widgets.data.linechart;

import java.util.List;

import io.github.digitalsmile.goldberry.kdl.KdlNode;
import io.github.digitalsmile.goldberry.widget.Widget;
import io.github.digitalsmile.goldberry.widget.attr.Attributed;
import io.github.digitalsmile.goldberry.widget.attr.Attributes;
import io.github.digitalsmile.goldberry.widgets.data.Series;
import io.github.digitalsmile.goldberry.widgets.markup.Markup;
import io.github.digitalsmile.goldberry.widgets.markup.Wiring;

/// A trend with axes — `docs/core-widgets.md` §11's `line-chart`.
///
/// ```java
/// new LineChart(List.of(
///         Series.of("Downloads", 12, 19, 15, 27, 31),
///         Series.of("Installs", 8, 11, 9, 18, 21)))
///     .categories(List.of("0.1", "0.2", "0.3", "0.4", "0.5"));
/// ```
///
/// ```kdl
/// line-chart {
///     series name="downloads" { point "0.1" 1200; point "0.2" 3400 }
/// }
/// ```
///
/// ## Two halves, and the split is the design
///
/// A **plot**, which is a `canvas` — one painter, one polyline per series, no
/// node per point, because a chart of a thousand points is not a tree of a
/// thousand nodes. And a **legend**, which is ordinary widgets: text and a
/// swatch, so a stylesheet reaches them, the shaping cache serves them, and the
/// entries wrap when the chart is narrow. Drawing the legend inside the canvas
/// would have re-implemented all three.
///
/// The legend appears for **two or more series and not for one**: with one line
/// the title names it, and a box repeating that is noise; with two, the colour is
/// the only thing telling them apart, so identity must never be colour alone.
///
/// ## What a pointer does
///
/// Hovering draws a **crosshair** at the nearest point, a marker on each series
/// there, and a readout of what they read; clicking a **legend entry** shows that
/// series alone and clicking it again puts them all back
/// (ADR-0198).
///
/// It answers the **keyboard** too, because §2.2 says everything is reachable:
/// `Left` and `Right` walk the crosshair, `Home` and `End` are the ends, and
/// `Escape` lets go
/// (ADR-0199).
///
/// ## Missing values
///
/// A hole is `Double.NaN` and a `null` is read as one, and what happens there is
/// [#nulls]: a **gap** by default, because it is the only one of the three that
/// invents nothing
/// (ADR-0201).
/// A chart with no values at all says so rather than drawing an empty grid
/// (ADR-0200),
/// and [#loading] and [#failed] are how an application says the rest.
///
/// ## Limits
///
/// [#threshold] draws one across the chart — a line or a shaded region, in one of
/// four semantic hues and never a series colour, and part of the domain so a
/// limit you have not reached is still on screen
/// (ADR-0202).
///
/// ## When, rather than which
///
/// [#times] gives the chart one `Instant` per point, and the x becomes time: an
/// unscraped stretch is as wide as it was long, and the labels step across
/// second, minute, hour, day, month and year boundaries
/// (ADR-0203).
///
/// ## Straight, smooth or stepped
///
/// [#curve] decides what the line claims happened between two readings. The
/// default is straight; **smooth is monotone**, so it cannot draw a percentage
/// below zero on its way up
/// (ADR-0204).
///
/// ## Under the line
///
/// [#fill] puts a flat wash or a fade beneath the data — `charts.md` §3.1's
/// last unbuilt row, and the one that cost a widening of the native surface
/// before a single pixel of it could be drawn: Blend2D has gradients and the
/// export list did not
/// (ADR-0207).
/// The default is no fill at all, so a chart nobody asked keeps exactly the
/// picture it had — see
/// [io.github.digitalsmile.goldberry.widgets.data.Fill].
///
/// **No dual y-axis, ever.** Two measures at different scales are two charts, or
/// one indexed to a common base; a second y-scale is the most reliable way to
/// make a chart say something untrue, and `charts.md` §3.4 refuses it in as many
/// words.
///
/// @param series     the lines, in order — which is also their colour order
///                   (ADR-0194)
/// @param categories a label per point, or empty for no x labels
/// @param attributes `id` and `class`, exactly as on the primitives
@Markup("line-chart")
public record LineChart(
        List<Series> series,
        List<String> categories,
        io.github.digitalsmile.goldberry.widgets.data.ChartOptions options,
        Attributes attributes)
        implements Widget.Stateful, io.github.digitalsmile.goldberry.widgets.data.ChartSpec, Attributed<LineChart> {

    public LineChart {
        series = List.copyOf(series == null ? List.of() : series);
        categories = List.copyOf(categories == null ? List.of() : categories);
        attributes = attributes == null ? Attributes.NONE : attributes;
        options = options == null ? io.github.digitalsmile.goldberry.widgets.data.ChartOptions.DEFAULTS : options;
    }

    /// The ordinary form: a chart that has its data.
    public LineChart(List<Series> series, List<String> categories, Attributes attributes) {
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
    public LineChart softAxis(double min, double max) {
        return options(options.bounds(io.github.digitalsmile.goldberry.widgets.data.Bounds.soft(min, max)));
    }

    /// This chart with an axis that is **exactly** `min…max`, whatever the data
    /// does.
    ///
    /// For a range that is a definition rather than an observation — a percentage
    /// of a whole, a gauge with a physical stop. Data outside it is drawn outside
    /// the plot and clipped, which is the correct rendering of a promise that was
    /// wrong.
    public LineChart axis(double min, double max) {
        return options(options.bounds(io.github.digitalsmile.goldberry.widgets.data.Bounds.hard(min, max)));
    }

    /// This chart sharing its crosshair with every other chart in `group`.
    ///
    /// Pointing at Tuesday here puts the crosshair on Tuesday on all of them,
    /// which is how a reader asks what the other panel was doing at the same
    /// moment. Only the chart under the pointer draws the readout
    /// ([io.github.digitalsmile.goldberry.widgets.data.CrosshairGroup]).
    public LineChart crosshair(io.github.digitalsmile.goldberry.widgets.data.CrosshairGroup group) {

        return options(options.crosshair(group));
    }

    /// This chart with a different marker rule — a dot at each reading, or not.
    public LineChart markers(io.github.digitalsmile.goldberry.widgets.data.Markers value) {
        return options(options.markers(value));
    }

    /// This chart with something **under** the line.
    ///
    /// A line chart has nothing under it by default, which is what a line chart
    /// is: the position is the reading and an area would be claiming a second
    /// encoding for it. [io.github.digitalsmile.goldberry.widgets.data.Fill#GRADIENT]
    /// is the dashboard convention and says what it means — the fade thins out
    /// downward, so the line stays the data and the area is a hint at magnitude
    /// (ADR-0207).
    ///
    /// The fill runs down to **zero**, or to the bottom of the plot on a log
    /// axis, where zero has no position at all.
    public LineChart fill(io.github.digitalsmile.goldberry.widgets.data.Fill value) {
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
    /// (ADR-0205).
    /// Only `line-chart` draws one: a bar and a band are lengths from zero, and
    /// zero is not on the axis.
    public LineChart logY() {
        return options(options.logY(true));
    }

    /// This chart with a different interpolation — how the line gets from one
    /// point to the next.
    ///
    /// The default is [io.github.digitalsmile.goldberry.widgets.data.Curve#LINEAR],
    /// which makes the weakest claim about what happened in between. A
    /// `bar-chart` ignores it: a bar is a length rather than a path.
    public LineChart curve(io.github.digitalsmile.goldberry.widgets.data.Curve value) {
        return options(options.curve(value));
    }

    /// This chart with `value` as everything that is not its numbers.
    public LineChart options(io.github.digitalsmile.goldberry.widgets.data.ChartOptions value) {
        return new LineChart(series, categories, value, attributes);
    }

    /// This chart with a **time axis**: one instant per point, so the x is when
    /// rather than which.
    ///
    /// A gap in the sampling becomes a gap on the axis, and the labels step
    /// across second, minute, hour, day, month and year boundaries
    /// ([io.github.digitalsmile.goldberry.widgets.data.TimeAxis]).
    public LineChart times(List<java.time.Instant> value) {
        return options(options.time(io.github.digitalsmile.goldberry.widgets.data.TimeAxis.of(value)));
    }

    /// The same, in a zone the application chooses — a server's clock, or `UTC`
    /// for a test.
    public LineChart times(List<java.time.Instant> value, java.time.ZoneId zone) {
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
    public LineChart threshold(io.github.digitalsmile.goldberry.widgets.data.Threshold limit) {
        return options(options.threshold(limit));
    }

    /// This chart with exactly these limits, replacing whatever it had.
    public LineChart thresholds(List<io.github.digitalsmile.goldberry.widgets.data.Threshold> limits) {

        return options(options.thresholds(limits));
    }

    /// This chart, told what to do where a series has no value.
    ///
    /// The default is [io.github.digitalsmile.goldberry.widgets.data.NullPolicy#GAP],
    /// which is the only one of the three that invents nothing.
    public LineChart nulls(io.github.digitalsmile.goldberry.widgets.data.NullPolicy value) {
        return options(options.nulls(value));
    }

    /// This chart, waiting for its data — it keeps its box and says so.
    ///
    /// The box is the point: a panel whose charts vanished while their queries
    /// resolved would reflow twice per chart (ChartStatus).
    public LineChart loading() {
        return status(io.github.digitalsmile.goldberry.widgets.data.ChartStatus.loading());
    }

    /// This chart, in the application's own words about why there is nothing.
    public LineChart failed(String message) {
        return status(io.github.digitalsmile.goldberry.widgets.data.ChartStatus.failed(message));
    }

    /// This chart with `value` as its state — see
    /// [io.github.digitalsmile.goldberry.widgets.data.ChartStatus].
    public LineChart status(io.github.digitalsmile.goldberry.widgets.data.ChartStatus value) {
        return options(options.status(value));
    }

    public LineChart(List<Series> series) {
        this(series, List.of(), Attributes.NONE);
    }

    /// This chart with a label under each point.
    public LineChart categories(List<String> values) {
        return new LineChart(series, values, options, attributes);
    }

    /// The type of the box this chart's view draws — see
    /// [io.github.digitalsmile.goldberry.widgets.data.ChartSpec#chartType()].
    @Override
    public String chartType() {
        return "line-chart";
    }

    @Override
    public io.github.digitalsmile.goldberry.widgets.data.ChartParts.Mode mode() {
        return io.github.digitalsmile.goldberry.widgets.data.ChartParts.Mode.LINE;
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
    public LineChart withAttributes(Attributes value) {
        return new LineChart(series, categories, options, value);
    }

    /// Builds a `line-chart` from markup — §3.2's inline form, for small static
    /// data.
    ///
    /// ```kdl
    /// line-chart {
    ///     series name="downloads" { point "0.1" 1200; point "0.2" 3400 }
    /// }
    /// ```
    ///
    /// The categories come from the **first** series' point names, because they
    /// are the x axis and a chart has one: a second series naming its points
    /// differently would be two x axes in one picture, which is the same mistake
    /// as two y axes and is refused the same way — by there being nowhere to put
    /// it.
    public static Widget inflate(KdlNode node, List<Widget> children, Wiring wiring) {
        var read = io.github.digitalsmile.goldberry.widgets.data.ChartParts.read(children);
        return new LineChart(read.series(), read.categories(), Attributes.of(node));
    }
}
