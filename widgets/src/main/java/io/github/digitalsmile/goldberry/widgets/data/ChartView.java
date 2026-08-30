package io.github.digitalsmile.goldberry.widgets.data;

import java.util.List;
import java.util.Set;

import io.github.digitalsmile.goldberry.css.ComputedStyle;
import io.github.digitalsmile.goldberry.paint.Box;
import io.github.digitalsmile.goldberry.widget.Widget;
import io.github.digitalsmile.goldberry.widget.attr.Attributes;
import io.github.digitalsmile.goldberry.widget.style.Paints;
import io.github.digitalsmile.goldberry.widget.style.Styled;

/// A chart's own box — the column its plot and legend sit in.
///
/// The half of a chart widget that is a *box* rather than a *state*. A chart is
/// stateful (see [ChartSpec]), and a stateful widget builds widgets instead of
/// producing a box, so this is what it builds: the same box the chart used to
/// render itself, with the same CSS type, the same `id` and the same classes.
///
/// **The box tree is unchanged**, which is the point of doing it this way rather
/// than wrapping the parts in a node of their own. `line-chart > chart-plot` is
/// still exactly that, every rule in `controls.css` still lands where it did,
/// and every golden image is still the same picture — a stateful widget occupies
/// an element and no box, so the only thing this added was a place to keep an
/// integer.
///
/// @param cssType    the chart's own type, from [ChartSpec#chartType()]
/// @param parts      the plot, and the legend when there is one
/// @param attributes the chart's, so a rule naming the chart still matches
record ChartView(String cssType, List<Widget> parts, Attributes attributes) implements Widget.Leaf, Styled, Paints {

    ChartView {
        parts = List.copyOf(parts == null ? List.of() : parts);
        attributes = attributes == null ? Attributes.NONE : attributes;
    }

    @Override
    public String cssType() {
        return cssType;
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
    public List<Widget> children() {
        return parts;
    }

    @Override
    public Box render(ComputedStyle style, List<Box> children, Context context) {
        return Box.of().style(style).children(children.toArray(Box[]::new));
    }
}
