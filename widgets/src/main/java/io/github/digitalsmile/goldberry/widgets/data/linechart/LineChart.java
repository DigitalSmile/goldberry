package io.github.digitalsmile.goldberry.widgets.data.linechart;

import io.github.digitalsmile.goldberry.css.ComputedStyle;
import io.github.digitalsmile.goldberry.kdl.KdlNode;
import io.github.digitalsmile.goldberry.paint.Box;
import io.github.digitalsmile.goldberry.widget.attr.Attributed;
import io.github.digitalsmile.goldberry.widget.attr.Attributes;
import io.github.digitalsmile.goldberry.widget.style.Paints;
import io.github.digitalsmile.goldberry.widget.style.Styled;
import io.github.digitalsmile.goldberry.widget.Widget;
import io.github.digitalsmile.goldberry.widgets.data.Series;
import io.github.digitalsmile.goldberry.widgets.markup.Markup;
import io.github.digitalsmile.goldberry.widgets.markup.Wiring;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

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
/// ([ADR-0198](../../../../../../../../book/src/adr/0198-a-charts-readout-is-painted-and-its-legend-is-a-control.md)).
///
/// It answers the **keyboard** too, because §2.2 says everything is reachable:
/// `Left` and `Right` walk the crosshair, `Home` and `End` are the ends, and
/// `Escape` lets go
/// ([ADR-0199](../../../../../../../../book/src/adr/0199-a-chart-answers-the-keyboard-and-a-step-is-relative.md)).
///
/// ## Missing values
///
/// A hole is `Double.NaN` and a `null` is read as one, and what happens there is
/// [#nulls]: a **gap** by default, because it is the only one of the three that
/// invents nothing
/// ([ADR-0201](../../../../../../../../book/src/adr/0201-a-hole-is-not-a-zero.md)).
/// A chart with no values at all says so rather than drawing an empty grid
/// ([ADR-0200](../../../../../../../../book/src/adr/0200-a-chart-with-no-data-says-so.md)),
/// and [#loading] and [#failed] are how an application says the rest.
///
/// ## What it does not have yet
///
/// Thresholds, log scales, time axes, interpolation and a crosshair shared with
/// the chart beside it — `charts.md` §3.1 is the list. The x is the point
/// **index**; [#categories] labels the points and a `java.time` axis is §3.1's
/// and is not built.
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
public record LineChart(List<Series> series, List<String> categories,
        io.github.digitalsmile.goldberry.widgets.data.ChartStatus status,
        io.github.digitalsmile.goldberry.widgets.data.NullPolicy nulls,
        Attributes attributes)
        implements Widget.Stateful, io.github.digitalsmile.goldberry.widgets.data.ChartSpec,
                Attributed<LineChart> {

    public LineChart {
        series = List.copyOf(series == null ? List.of() : series);
        categories = List.copyOf(categories == null ? List.of() : categories);
        attributes = attributes == null ? Attributes.NONE : attributes;
        status = status == null
                ? io.github.digitalsmile.goldberry.widgets.data.ChartStatus.READY : status;
        nulls = nulls == null
                ? io.github.digitalsmile.goldberry.widgets.data.NullPolicy.GAP : nulls;
    }

    /// The ordinary form: a chart that has its data.
    public LineChart(List<Series> series, List<String> categories, Attributes attributes) {
        this(series, categories,
                io.github.digitalsmile.goldberry.widgets.data.ChartStatus.READY,
                io.github.digitalsmile.goldberry.widgets.data.NullPolicy.GAP, attributes);
    }

    /// This chart, told what to do where a series has no value.
    ///
    /// The default is [io.github.digitalsmile.goldberry.widgets.data.NullPolicy#GAP],
    /// which is the only one of the three that invents nothing.
    public LineChart nulls(io.github.digitalsmile.goldberry.widgets.data.NullPolicy value) {
        return new LineChart(series, categories, status, value, attributes);
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
        return new LineChart(series, categories, value, nulls, attributes);
    }

    public LineChart(List<Series> series) {
        this(series, List.of(), Attributes.NONE);
    }

    /// This chart with a label under each point.
    public LineChart categories(List<String> values) {
        return new LineChart(series, values, status, nulls, attributes);
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
        return new LineChart(series, categories, status, nulls, value);
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
