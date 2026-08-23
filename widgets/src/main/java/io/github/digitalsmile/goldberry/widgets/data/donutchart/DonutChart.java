package io.github.digitalsmile.goldberry.widgets.data.donutchart;

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
import io.github.digitalsmile.goldberry.widgets.data.SeriesPalette;
import io.github.digitalsmile.goldberry.widgets.markup.Markup;
import io.github.digitalsmile.goldberry.widgets.markup.Wiring;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/// Part to whole — `docs/core-widgets.md` §11's `donut-chart`, and the last of
/// the five.
///
/// ```kdl
/// donut-chart {
///     series name="cache" { point "Cache" 62 }
///     series name="origin" { point "Origin" 24 }
///     series name="miss" { point "Miss" 14 }
/// }
/// ```
///
/// ## It refuses two slices and it refuses nine
///
/// Both refusals are at construction, which is where `dialog` refuses two
/// affirmative buttons and for the same reason: a widget that cannot mean
/// anything sensible should say so where the mistake is, not draw something
/// misleading and leave it to be noticed.
///
/// **Two slices is a [io.github.digitalsmile.goldberry.widgets.controls.progressbar.Progress]**,
/// or a meter. A reader comparing two arcs is doing badly what a single bar does
/// well, and "62% used" is a sentence a ring makes harder to read rather than
/// easier.
///
/// **Nine slices is a `bar-chart`.** Past about eight, arcs get too narrow to
/// compare and the palette has run out of hues that stay distinguishable under
/// colour-vision deficiency
/// ([ADR-0194](../../../../../../../../book/src/adr/0194-a-series-colour-is-derived-from-nord-not-taken-from-it.md)).
/// A bar chart answers the same question and keeps answering it at forty
/// categories.
///
/// So the legitimate range is three to eight, and inside it a donut does one
/// thing well: showing that a few parts make a whole.
///
/// @param slices     the parts, in order — which is also their colour order
/// @param attributes `id` and `class`, exactly as on the primitives
@Markup("donut-chart")
public record DonutChart(List<Series> slices, Attributes attributes)
        implements Widget.Leaf, Styled, Paints, Attributed<DonutChart> {

    /// The fewest slices that are a whole rather than a ratio. See the class
    /// note.
    public static final int MIN_SLICES = 3;

    /// The most that stay distinguishable — the palette's ceiling.
    public static final int MAX_SLICES = SeriesPalette.SLOTS;

    public DonutChart {
        slices = List.copyOf(slices == null ? List.of() : slices);
        attributes = attributes == null ? Attributes.NONE : attributes;
        if (!slices.isEmpty() && slices.size() < MIN_SLICES) {
            throw new IllegalArgumentException(
                    "a donut of " + slices.size() + " is a ratio rather than a whole; use"
                            + " `progress` for \"62% used\", which reads better as a bar");
        }
        if (slices.size() > MAX_SLICES) {
            throw new IllegalArgumentException(
                    "a donut of " + slices.size() + " slices has arcs too narrow to compare and"
                            + " more parts than there are distinguishable hues; use `bar-chart`,"
                            + " which answers the same question at forty categories");
        }
    }

    public DonutChart(List<Series> slices) {
        this(slices, Attributes.NONE);
    }

    @Override
    public String cssType() {
        return "donut-chart";
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
    public DonutChart withAttributes(Attributes value) {
        return new DonutChart(slices, value);
    }

    /// The ring, and a legend — which a donut **always** has, unlike the axis
    /// charts.
    ///
    /// An axis chart with one series is named by its title; a donut's slices are
    /// never one thing, and an arc has nowhere to write a name. So the legend is
    /// not optional here: without it the chart is a set of coloured shapes.
    @Override
    public List<Widget> children() {
        var values = new ArrayList<Double>(slices.size());
        var labels = new ArrayList<String>(slices.size());
        for (var slice : slices) {
            // One number per slice: a part-to-whole chart of a *series* would be
            // a chart of several wholes, which is a stacked bar.
            values.add(slice.values().isEmpty() ? 0 : slice.values().getFirst());
            labels.add(slice.name());
        }
        var parts = new ArrayList<Widget>(2);
        parts.add(new DonutPlot(values, labels));
        if (!slices.isEmpty()) {
            parts.add(new io.github.digitalsmile.goldberry.widgets.data.linechart.ChartLegend(
                    slices));
        }
        return List.copyOf(parts);
    }

    @Override
    public Box render(ComputedStyle style, List<Box> children, Context context) {
        return Box.of().style(style).children(children.toArray(Box[]::new));
    }

    /// Builds a `donut-chart` from markup — §3.2's inline form, one point per
    /// series.
    public static Widget inflate(KdlNode node, List<Widget> children, Wiring wiring) {
        return new DonutChart(ChartParts.read(children).series(), Attributes.of(node));
    }
}
