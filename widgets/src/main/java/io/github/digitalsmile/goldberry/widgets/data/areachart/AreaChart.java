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
public record AreaChart(List<Series> series, List<String> categories, Attributes attributes)
        implements Widget.Leaf, Styled, Paints, Attributed<AreaChart> {

    public AreaChart {
        series = List.copyOf(series == null ? List.of() : series);
        categories = List.copyOf(categories == null ? List.of() : categories);
        attributes = attributes == null ? Attributes.NONE : attributes;
    }

    public AreaChart(List<Series> series) {
        this(series, List.of(), Attributes.NONE);
    }

    /// This chart with a label under each point.
    public AreaChart categories(List<String> values) {
        return new AreaChart(series, values, attributes);
    }

    @Override
    public String cssType() {
        return "area-chart";
    }

    @Override
    public String id() {
        return attributes.id();
    }

    @Override
    public Set<String> classes() {
        return attributes.classes();
    }

    @Override
    public Object key() {
        return attributes.key();
    }

    @Override
    public AreaChart withAttributes(Attributes value) {
        return new AreaChart(series, categories, value);
    }

    @Override
    public List<Widget> children() {
        return ChartParts.of(series, categories, ChartParts.Mode.AREA);
    }

    @Override
    public Box render(ComputedStyle style, List<Box> children, Context context) {
        return Box.of().style(style).children(children.toArray(Box[]::new));
    }

    /// Builds an `area-chart` from markup — §3.2's inline form.
    public static Widget inflate(KdlNode node, List<Widget> children, Wiring wiring) {
        var read = ChartParts.read(children);
        return new AreaChart(read.series(), read.categories(), Attributes.of(node));
    }
}
