package io.github.digitalsmile.goldberry.widgets.data.linechart;

import java.util.List;

import io.github.digitalsmile.goldberry.kdl.KdlNode;
import io.github.digitalsmile.goldberry.widget.State;
import io.github.digitalsmile.goldberry.widget.Widget;
import io.github.digitalsmile.goldberry.widget.attr.Attributed;
import io.github.digitalsmile.goldberry.widget.attr.Attributes;
import io.github.digitalsmile.goldberry.widgets.data.ChartOptions;
import io.github.digitalsmile.goldberry.widgets.data.ChartParts;
import io.github.digitalsmile.goldberry.widgets.data.ChartSpec;
import io.github.digitalsmile.goldberry.widgets.data.Fill;
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
/// ## The knobs
///
/// [ChartSpec] carries them, because all three axis charts take the same ones and
/// three copies of a wither is three chances for one of them to describe a picture
/// the painter does not draw. This is the chart that draws **every** one of them:
/// [ChartSpec#markers] puts a dot at each reading, [ChartSpec#logY] gives each
/// decade the same room, [ChartSpec#curve] decides what the line claims happened
/// between two readings — the default is straight and **smooth is monotone**, so
/// it cannot draw a percentage below zero on its way up (ADR-0204) — and
/// [ChartSpec#times] makes the x *when* rather than *which* (ADR-0203).
///
/// [#fill] is this chart's own: what goes **under** the line, which is a different
/// picture from `area-chart`'s bands and is not a picture a bar has at all.
///
/// ## Missing values
///
/// A hole is `Double.NaN` and a `null` is read as one, and what happens there is
/// [ChartSpec#nulls]: a **gap** by default, because it is the only one of the
/// three that invents nothing
/// (ADR-0201).
/// A chart with no values at all says so rather than drawing an empty grid
/// (ADR-0200),
/// and [ChartSpec#loading] and [ChartSpec#failed] are how an application says the
/// rest.
///
/// ## Limits
///
/// [ChartSpec#threshold] draws one across the chart — a line or a shaded region,
/// in one of four semantic hues and never a series colour, and part of the domain
/// so a limit you have not reached is still on screen
/// (ADR-0202).
///
/// ## When, rather than which
///
/// [ChartSpec#times] gives the chart one `Instant` per point, and the x becomes
/// time: an unscraped stretch is as wide as it was long, and the labels step
/// across second, minute, hour, day, month and year boundaries
/// (ADR-0203).
///
/// ## Straight, smooth or stepped
///
/// [ChartSpec#curve] decides what the line claims happened between two readings.
/// The default is straight; **smooth is monotone**, so it cannot draw a
/// percentage below zero on its way up
/// (ADR-0204).
///
/// ## Under the line
///
/// [ChartSpec#fill] puts a flat wash or a fade beneath the data — `charts.md`
/// §3.1's last row to be built, and the one that cost a widening of the native
/// surface before a single pixel of it could be drawn: Blend2D has gradients and
/// the export list did not
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
public record LineChart(List<Series> series, List<String> categories, ChartOptions options, Attributes attributes)
        implements Widget.Stateful, ChartSpec<LineChart>, Attributed<LineChart> {

    public LineChart {
        series = List.copyOf(series == null ? List.of() : series);
        categories = List.copyOf(categories == null ? List.of() : categories);
        attributes = attributes == null ? Attributes.NONE : attributes;
        options = options == null ? ChartOptions.DEFAULTS : options;
    }

    /// The ordinary form: a chart that has its data.
    public LineChart(List<Series> series, List<String> categories, Attributes attributes) {
        this(series, categories, ChartOptions.DEFAULTS, attributes);
    }

    public LineChart(List<Series> series) {
        this(series, List.of(), Attributes.NONE);
    }

    /// This chart with something **under** the line.
    ///
    /// A line chart has nothing under it by default, which is what a line chart
    /// is: the position is the reading and an area would be claiming a second
    /// encoding for it. [Fill#GRADIENT] is the dashboard convention and says what
    /// it means — the fade thins out downward, so the line stays the data and the
    /// area is a hint at magnitude
    /// (ADR-0207).
    ///
    /// The fill runs down to **zero**, or to the bottom of the plot on a log
    /// axis, where zero has no position at all.
    ///
    /// Here rather than on [ChartSpec] because the three charts do not agree about
    /// it: an `area-chart`'s fill is each band within its own extent, and a
    /// `bar-chart` has no region under anything to fade.
    public LineChart fill(Fill value) {
        return options(options.fill(value));
    }

    /// This chart with `value` as everything that is not its numbers.
    @Override
    public LineChart options(ChartOptions value) {
        return new LineChart(series, categories, value, attributes);
    }

    /// This chart with a label under each point.
    public LineChart categories(List<String> values) {
        return new LineChart(series, values, options, attributes);
    }

    /// The type of the box this chart's view draws — see [ChartSpec#chartType()].
    @Override
    public String chartType() {
        return "line-chart";
    }

    @Override
    public ChartParts.Mode mode() {
        return ChartParts.Mode.LINE;
    }

    /// The state both halves of this chart read — which series is isolated.
    @Override
    public State<?> createState() {
        return ChartParts.state();
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
        var read = ChartParts.read(children);
        return new LineChart(read.series(), read.categories(), Attributes.of(node));
    }
}
