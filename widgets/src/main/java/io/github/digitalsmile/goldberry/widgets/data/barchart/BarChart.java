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
public record BarChart(List<Series> series, List<String> categories, Attributes attributes)
        implements Widget.Stateful, io.github.digitalsmile.goldberry.widgets.data.ChartSpec,
                Attributed<BarChart> {

    public BarChart {
        series = List.copyOf(series == null ? List.of() : series);
        categories = List.copyOf(categories == null ? List.of() : categories);
        attributes = attributes == null ? Attributes.NONE : attributes;
    }

    public BarChart(List<Series> series) {
        this(series, List.of(), Attributes.NONE);
    }

    /// This chart with a label under each group.
    public BarChart categories(List<String> values) {
        return new BarChart(series, values, attributes);
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
        return new BarChart(series, categories, value);
    }

    /// Builds a `bar-chart` from markup — §3.2's inline form.
    public static Widget inflate(KdlNode node, List<Widget> children, Wiring wiring) {
        var read = ChartParts.read(children);
        return new BarChart(read.series(), read.categories(), Attributes.of(node));
    }
}
