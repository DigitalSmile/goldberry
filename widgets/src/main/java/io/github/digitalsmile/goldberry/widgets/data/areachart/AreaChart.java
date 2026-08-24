package io.github.digitalsmile.goldberry.widgets.data.areachart;

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
public record AreaChart(List<Series> series, List<String> categories,
        io.github.digitalsmile.goldberry.widgets.data.ChartStatus status,
        io.github.digitalsmile.goldberry.widgets.data.NullPolicy nulls,
        List<io.github.digitalsmile.goldberry.widgets.data.Threshold> thresholds,
        Attributes attributes)
        implements Widget.Stateful, io.github.digitalsmile.goldberry.widgets.data.ChartSpec,
                Attributed<AreaChart> {

    public AreaChart {
        series = List.copyOf(series == null ? List.of() : series);
        categories = List.copyOf(categories == null ? List.of() : categories);
        attributes = attributes == null ? Attributes.NONE : attributes;
        status = status == null
                ? io.github.digitalsmile.goldberry.widgets.data.ChartStatus.READY : status;
        nulls = nulls == null
                ? io.github.digitalsmile.goldberry.widgets.data.NullPolicy.GAP : nulls;
        thresholds = List.copyOf(thresholds == null ? List.of() : thresholds);
    }

    /// The ordinary form: a chart that has its data.
    public AreaChart(List<Series> series, List<String> categories, Attributes attributes) {
        this(series, categories,
                io.github.digitalsmile.goldberry.widgets.data.ChartStatus.READY,
                io.github.digitalsmile.goldberry.widgets.data.NullPolicy.GAP,
                List.of(), attributes);
    }

    /// This chart with `limit` drawn across it — a line or a shaded region, in
    /// one of the four semantic hues.
    ///
    /// Additive, so several limits read as several calls:
    /// `chart.threshold(warn).threshold(fail)`. A threshold is part of the
    /// domain, so one you have not crossed yet is still on screen
    /// ([io.github.digitalsmile.goldberry.widgets.data.Threshold]).
    public AreaChart threshold(io.github.digitalsmile.goldberry.widgets.data.Threshold limit) {
        var next = new java.util.ArrayList<>(thresholds);
        next.add(java.util.Objects.requireNonNull(limit, "limit"));
        return new AreaChart(series, categories, status, nulls, List.copyOf(next), attributes);
    }

    /// This chart with exactly these limits, replacing whatever it had.
    public AreaChart thresholds(
            List<io.github.digitalsmile.goldberry.widgets.data.Threshold> limits) {

        return new AreaChart(series, categories, status, nulls, limits, attributes);
    }

    /// This chart, told what to do where a series has no value.
    ///
    /// The default is [io.github.digitalsmile.goldberry.widgets.data.NullPolicy#GAP],
    /// which is the only one of the three that invents nothing.
    public AreaChart nulls(io.github.digitalsmile.goldberry.widgets.data.NullPolicy value) {
        return new AreaChart(series, categories, status, value, thresholds, attributes);
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
        return new AreaChart(series, categories, value, nulls, thresholds, attributes);
    }

    public AreaChart(List<Series> series) {
        this(series, List.of(), Attributes.NONE);
    }

    /// This chart with a label under each point.
    public AreaChart categories(List<String> values) {
        return new AreaChart(series, values, status, nulls, thresholds, attributes);
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
        return new AreaChart(series, categories, status, nulls, thresholds, value);
    }

    /// Builds an `area-chart` from markup — §3.2's inline form.
    public static Widget inflate(KdlNode node, List<Widget> children, Wiring wiring) {
        var read = ChartParts.read(children);
        return new AreaChart(read.series(), read.categories(), Attributes.of(node));
    }
}
