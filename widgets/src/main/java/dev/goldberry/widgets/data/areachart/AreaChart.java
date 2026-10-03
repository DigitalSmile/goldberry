package dev.goldberry.widgets.data.areachart;

import java.util.List;

import org.jspecify.annotations.Nullable;

import dev.goldberry.kdl.KdlNode;
import dev.goldberry.widget.State;
import dev.goldberry.widget.Widget;
import dev.goldberry.widget.attr.Attributed;
import dev.goldberry.widget.attr.Attributes;
import dev.goldberry.widgets.data.ChartOptions;
import dev.goldberry.widgets.data.ChartParts;
import dev.goldberry.widgets.data.ChartSpec;
import dev.goldberry.widgets.data.Fill;
import dev.goldberry.widgets.data.Series;
import dev.goldberry.widgets.markup.Markup;
import dev.goldberry.widgets.markup.Wiring;

/// A total, and what it is made of: a stacked area chart, one band per series.
///
/// ```kdl
/// area-chart {
///     series name="cache" { point "Mon" 40; point "Tue" 52 }
///     series name="origin" { point "Mon" 12; point "Tue" 9 }
/// }
/// ```
///
/// In Java, `new AreaChart(series)`, then [#categories], [#fill] and the
/// [ChartSpec] withers — `curve`, `times`, `threshold`, `softAxis`, `loading`
/// — as needed. The `series` children are the data; everything else is the
/// chart's `id` and `class`.
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
///
/// ## The knobs
///
/// [ChartSpec] carries them. This chart draws [ChartSpec#curve] — on **both edges**
/// of a band, so the fill between them stays the difference its numbers say — and
/// [ChartSpec#times], and ignores [ChartSpec#markers] and [ChartSpec#logY], which
/// each say so. [#fill] is this chart's own, because a band fades within its own
/// extent and a line's fill fades under the line.
///
/// Read more: [Charts](https://goldberry.dev/docs/components/charts.html#area-chart).
@Markup("area-chart")
public record AreaChart(List<Series> series, List<String> categories, ChartOptions options, Attributes attributes)
        implements Widget.Stateful, ChartSpec<AreaChart>, Attributed<AreaChart> {

    /// The canonical constructor, written out so that the parameters taking null for a default can say so.
    public AreaChart(
            @Nullable List<Series> series,
            @Nullable List<String> categories,
            @Nullable ChartOptions options,
            @Nullable Attributes attributes) {
        series = List.copyOf(series == null ? List.of() : series);
        categories = List.copyOf(categories == null ? List.of() : categories);
        attributes = attributes == null ? Attributes.NONE : attributes;
        options = options == null ? ChartOptions.DEFAULTS : options;
        this.series = series;
        this.categories = categories;
        this.options = options;
        this.attributes = attributes;
    }

    /// The ordinary form: a chart that has its data.
    public AreaChart(List<Series> series, List<String> categories, Attributes attributes) {
        this(series, categories, ChartOptions.DEFAULTS, attributes);
    }

    public AreaChart(List<Series> series) {
        this(series, List.of(), Attributes.NONE);
    }

    /// This chart with a different fill in its bands.
    ///
    /// A flat wash by default, which is the honest rendering of a stack: the
    /// bands are adjacent rather than overlapping, they add up to the total, and
    /// a reader compares their thicknesses. [Fill#GRADIENT] fades each band
    /// **within its own extent** rather than across the plot, so a thin band under
    /// a thick one is still there to be read.
    ///
    /// [Fill#NONE] is read as a flat wash: a band with no fill is not a band.
    ///
    /// Here rather than on [ChartSpec] because the three charts do not agree about
    /// it: a `line-chart`'s fill is the region under one line, and a `bar-chart`
    /// has no such region at all.
    public AreaChart fill(Fill value) {
        return options(options.fill(value));
    }

    /// This chart with `value` as everything that is not its numbers.
    @Override
    public AreaChart options(ChartOptions value) {
        return new AreaChart(series, categories, value, attributes);
    }

    /// This chart with a label under each point.
    public AreaChart categories(List<String> values) {
        return new AreaChart(series, values, options, attributes);
    }

    /// The type of the box this chart's view draws — see [ChartSpec#chartType()].
    @Override
    public String chartType() {
        return "area-chart";
    }

    @Override
    public ChartParts.Mode mode() {
        return ChartParts.Mode.AREA;
    }

    /// The state both halves of this chart read — which series is isolated.
    @Override
    public State<?> createState() {
        return ChartParts.state();
    }

    @Override
    public @Nullable Object key() {
        return attributes.key();
    }

    @Override
    public AreaChart withAttributes(Attributes value) {
        return new AreaChart(series, categories, options, value);
    }

    /// Builds an `area-chart` from markup, reading its inline `series` children.
    @SuppressWarnings("unused")
    public static Widget inflate(KdlNode node, List<Widget> children, Wiring wiring) {
        var read = ChartParts.read(children);
        return new AreaChart(read.series(), read.categories(), Attributes.of(node));
    }
}
