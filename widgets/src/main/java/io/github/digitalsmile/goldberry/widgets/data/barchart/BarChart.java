package io.github.digitalsmile.goldberry.widgets.data.barchart;

import java.util.List;

import io.github.digitalsmile.goldberry.kdl.KdlNode;
import io.github.digitalsmile.goldberry.widget.State;
import io.github.digitalsmile.goldberry.widget.Widget;
import io.github.digitalsmile.goldberry.widget.attr.Attributed;
import io.github.digitalsmile.goldberry.widget.attr.Attributes;
import io.github.digitalsmile.goldberry.widgets.data.ChartOptions;
import io.github.digitalsmile.goldberry.widgets.data.ChartParts;
import io.github.digitalsmile.goldberry.widgets.data.ChartSpec;
import io.github.digitalsmile.goldberry.widgets.data.Series;
import io.github.digitalsmile.goldberry.widgets.markup.Markup;
import io.github.digitalsmile.goldberry.widgets.markup.Wiring;

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
///
/// ## The knobs it draws, and the ones it does not
///
/// [ChartSpec] carries them all, and this chart is the one that ignores the most
/// of them: [ChartSpec#markers], [ChartSpec#logY], [ChartSpec#curve] and
/// [ChartSpec#times] each draw the same pixels here as leaving them alone does,
/// and each says so where it is declared. A bar is a length from zero, so there is
/// nowhere to put a dot, no path between readings to bend, and no axis for a
/// logarithm or an instant to sit on. There is no `fill` at all: a bar is a solid
/// rectangle with no region under it for a ramp to cross.
///
/// What it does draw is the domain and the annotations — [ChartSpec#softAxis],
/// [ChartSpec#axis], [ChartSpec#threshold], [ChartSpec#nulls], the crosshair and
/// the three states of [ChartSpec#status].
@Markup("bar-chart")
public record BarChart(List<Series> series, List<String> categories, ChartOptions options, Attributes attributes)
        implements Widget.Stateful, ChartSpec<BarChart>, Attributed<BarChart> {

    public BarChart {
        series = List.copyOf(series == null ? List.of() : series);
        categories = List.copyOf(categories == null ? List.of() : categories);
        attributes = attributes == null ? Attributes.NONE : attributes;
        options = options == null ? ChartOptions.DEFAULTS : options;
    }

    /// The ordinary form: a chart that has its data.
    public BarChart(List<Series> series, List<String> categories, Attributes attributes) {
        this(series, categories, ChartOptions.DEFAULTS, attributes);
    }

    public BarChart(List<Series> series) {
        this(series, List.of(), Attributes.NONE);
    }

    /// This chart with `value` as everything that is not its numbers.
    @Override
    public BarChart options(ChartOptions value) {
        return new BarChart(series, categories, value, attributes);
    }

    /// This chart with a label under each group.
    public BarChart categories(List<String> values) {
        return new BarChart(series, values, options, attributes);
    }

    /// The type of the box this chart's view draws — see [ChartSpec#chartType()].
    @Override
    public String chartType() {
        return "bar-chart";
    }

    @Override
    public ChartParts.Mode mode() {
        return ChartParts.Mode.BAR;
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
    public BarChart withAttributes(Attributes value) {
        return new BarChart(series, categories, options, value);
    }

    /// Builds a `bar-chart` from markup — §3.2's inline form.
    public static Widget inflate(KdlNode node, List<Widget> children, Wiring wiring) {
        var read = ChartParts.read(children);
        return new BarChart(read.series(), read.categories(), Attributes.of(node));
    }
}
