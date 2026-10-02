package dev.goldberry.widgets.data.donutchart;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import org.jspecify.annotations.Nullable;

import dev.goldberry.css.ComputedStyle;
import dev.goldberry.kdl.KdlNode;
import dev.goldberry.paint.Box;
import dev.goldberry.widget.Widget;
import dev.goldberry.widget.attr.Attributed;
import dev.goldberry.widget.attr.Attributes;
import dev.goldberry.widget.style.Paints;
import dev.goldberry.widget.style.Styled;
import dev.goldberry.widgets.data.ChartParts;
import dev.goldberry.widgets.data.Series;
import dev.goldberry.widgets.data.SeriesPalette;
import dev.goldberry.widgets.markup.Markup;
import dev.goldberry.widgets.markup.Wiring;

/// Part to whole: three to eight slices of one ring, with a legend that is
/// always shown.
///
/// ```kdl
/// donut-chart {
///     series name="cache" { point "Cache" 62 }
///     series name="origin" { point "Origin" 24 }
///     series name="miss" { point "Miss" 14 }
/// }
/// ```
///
/// In Java, `new DonutChart(List.of(Series.of("cache", 62), …))`. One number
/// per slice: the first value of each series. [#loading] and [#failed] are its
/// only knobs — a donut has no axes, no crosshair and no isolation. Hovering a
/// slice, or walking the ring with `Left` and `Right`, shows its share in the
/// hole.
///
/// ## It refuses two slices and it refuses nine
///
/// Both refusals are at construction, which is where `dialog` refuses two
/// affirmative buttons and for the same reason: a widget that cannot mean
/// anything sensible should say so where the mistake is, not draw something
/// misleading and leave it to be noticed.
///
/// **Two slices is a [dev.goldberry.widgets.controls.progressbar.Progress]**,
/// or a meter. A reader comparing two arcs is doing badly what a single bar does
/// well, and "62% used" is a sentence a ring makes harder to read rather than
/// easier.
///
/// **Nine slices is a `bar-chart`.** Past about eight, arcs get too narrow to
/// compare and the palette has run out of hues that stay distinguishable under
/// colour-vision deficiency. A bar chart answers the same question and keeps
/// answering it at forty categories.
///
/// So the legitimate range is three to eight, and inside it a donut does one
/// thing well: showing that a few parts make a whole.
///
/// Read more: [Charts](https://goldberry.dev/docs/components/charts.html#donut-chart).
///
/// @param slices     the parts, in order — which is also their colour order
/// @param status     whether the chart has its data, is waiting, or gave up
/// @param attributes `id` and `class`, exactly as on the primitives
@Markup("donut-chart")
public record DonutChart(List<Series> slices, dev.goldberry.widgets.data.ChartStatus status, Attributes attributes)
        implements Widget.Leaf, Styled, Paints, Attributed<DonutChart> {

    /// The fewest slices that are a whole rather than a ratio. See the class
    /// note.
    public static final int MIN_SLICES = 3;

    /// The most that stay distinguishable — the palette's ceiling.
    public static final int MAX_SLICES = SeriesPalette.SLOTS;

    /// The canonical constructor, written out so that the parameters taking null for a default can say so.
    public DonutChart(
            @Nullable List<Series> slices,
            dev.goldberry.widgets.data.@Nullable ChartStatus status,
            @Nullable Attributes attributes) {
        slices = List.copyOf(slices == null ? List.of() : slices);
        attributes = attributes == null ? Attributes.NONE : attributes;
        status = status == null ? dev.goldberry.widgets.data.ChartStatus.READY : status;
        if (!slices.isEmpty() && slices.size() < MIN_SLICES) {
            throw new IllegalArgumentException("a donut of " + slices.size() + " is a ratio rather than a whole; use"
                    + " `progress` for \"62% used\", which reads better as a bar");
        }
        if (slices.size() > MAX_SLICES) {
            throw new IllegalArgumentException(
                    "a donut of " + slices.size() + " slices has arcs too narrow to compare and"
                            + " more parts than there are distinguishable hues; use `bar-chart`,"
                            + " which answers the same question at forty categories");
        }
        this.slices = slices;
        this.status = status;
        this.attributes = attributes;
    }

    /// The ordinary form: a donut that has its data.
    public DonutChart(List<Series> slices, Attributes attributes) {
        this(slices, dev.goldberry.widgets.data.ChartStatus.READY, attributes);
    }

    public DonutChart(List<Series> slices) {
        this(slices, Attributes.NONE);
    }

    /// This donut, waiting for its data — it keeps its box and says so.
    public DonutChart loading() {
        return status(dev.goldberry.widgets.data.ChartStatus.loading());
    }

    /// This donut, in the application's own words about why there is nothing.
    public DonutChart failed(String message) {
        return status(dev.goldberry.widgets.data.ChartStatus.failed(message));
    }

    /// This donut with `value` as its state.
    public DonutChart status(dev.goldberry.widgets.data.ChartStatus value) {
        return new DonutChart(slices, value, attributes);
    }

    @Override
    public String cssType() {
        return "donut-chart";
    }

    @Override
    public @Nullable String id() {
        return attributes.id();
    }

    @Override
    public Set<String> classes() {
        return attributes.classes();
    }

    @Override
    public @Nullable Object key() {
        return attributes.key();
    }

    @Override
    public DonutChart withAttributes(Attributes value) {
        return new DonutChart(slices, status, value);
    }

    /// The ring, and a legend — which a donut **always** has, unlike the axis
    /// charts.
    ///
    /// An axis chart with one series is named by its title; a donut's slices are
    /// never one thing, and an arc has nowhere to write a name. So the legend is
    /// not optional here: without it the chart is a set of coloured shapes.
    @Override
    public List<Widget> children() {
        // A ring of nothing is the same problem as a grid over nothing: an arc
        // asserts a share, and there is none. The one difference is what counts
        // as empty here -- a donut of three zeroes has no whole to be part of.
        var message = ChartParts.messageFor(status, hasWhole());
        if (message != null) {
            return List.of(message);
        }
        var values = new ArrayList<Double>(slices.size());
        var labels = new ArrayList<String>(slices.size());
        for (var slice : slices) {
            // One number per slice: a part-to-whole chart of a *series* would be
            // a chart of several wholes, which is a stacked bar.
            values.add(slice.values().isEmpty() ? 0 : slice.values().getFirst());
            labels.add(slice.name());
        }
        // A legend unconditionally: a donut with no slices never reaches here,
        // because a ring of nothing is the message above, and one with slices has
        // names that only the legend carries.
        return List.of(new DonutPlot(values, labels), new dev.goldberry.widgets.data.linechart.ChartLegend(slices));
    }

    @Override
    public Box render(ComputedStyle style, List<Box> children, Context context) {
        return Box.of().style(style).children(children.toArray(Box[]::new));
    }

    /// Whether these slices add up to anything.
    ///
    /// **A positive total**, not just a slice: three zeroes are three names and
    /// no whole, which is what a query returning rows of nulls looks like.
    private boolean hasWhole() {
        return slices.stream()
                        .mapToDouble(
                                one -> one.values().isEmpty() ? 0 : one.values().getFirst())
                        .filter(value -> value > 0)
                        .sum()
                > 0;
    }

    /// Builds a `donut-chart` from markup, reading its inline `series` children
    /// — one point per series.
    public static Widget inflate(KdlNode node, List<Widget> children, Wiring wiring) {
        return new DonutChart(ChartParts.read(children).series(), Attributes.of(node));
    }
}
