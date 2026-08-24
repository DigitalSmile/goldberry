package io.github.digitalsmile.goldberry.widgets.data;

import io.github.digitalsmile.goldberry.widget.Widget;
import io.github.digitalsmile.goldberry.widgets.data.linechart.ChartLegend;
import io.github.digitalsmile.goldberry.widgets.data.linechart.ChartPlot;
import io.github.digitalsmile.goldberry.widgets.data.linechart.ChartSeries;
import java.util.ArrayList;
import java.util.List;

/// What every chart in §11 is made of: a plot, and a legend when there is more
/// than one series.
///
/// Four widgets asking the same two questions — *what are my children* and *how
/// do I read §3.2's inline data* — and four copies of the answers would be four
/// chances for one chart to grow a legend rule the others do not have. The rule
/// is the same for all of them and lives here.
public final class ChartParts {

    /// Which shape the plot draws. A re-export of [ChartPlot]'s own, so a chart
    /// widget names a mode without reaching into the part that implements it.
    public enum Mode {
        LINE, AREA, BAR
    }

    private ChartParts() {
    }

    /// The state a [ChartSpec] creates — one class for all three axis charts.
    ///
    /// Here rather than exposed directly because [ChartState] is this package's
    /// and the charts are not: a chart says "my state is the chart state" and
    /// does not get to know what is in it.
    public static io.github.digitalsmile.goldberry.widget.State<?> state() {
        return new ChartState();
    }

    /// The plot, plus a legend when it is needed — for a chart nobody can
    /// interact with.
    ///
    /// What `donut-chart` builds, and what a test that only cares about the
    /// shape asks for.
    public static List<Widget> of(List<Series> series, List<String> categories, Mode mode) {
        return of(series, categories, mode, -1, null);
    }

    /// The same, for a chart whose legend can isolate a series.
    ///
    /// **Two series or more.** With one line the title names it and a legend box
    /// repeats it; with two, colour is the only thing telling them apart, so
    /// identity must never be colour alone.
    ///
    /// `isolated` reaches **both** halves, which is the whole reason it is
    /// threaded from above rather than held by either: the plot draws one series
    /// and the legend dims the rest, and a picture where those two disagreed
    /// would be a legend that names a line nobody can see.
    ///
    /// @param isolated  the series shown alone, or -1 for all of them
    /// @param onIsolate what a legend entry's click reports, or null for a
    ///                  legend that is a key rather than a control
    public static List<Widget> of(List<Series> series, List<String> categories, Mode mode,
            int isolated, java.util.function.IntConsumer onIsolate) {

        return of(series, categories, mode, isolated, onIsolate, ChartOptions.DEFAULTS);
    }

    /// The same, told everything about the chart that is not its numbers.
    ///
    /// **A sentence instead of a picture, and nothing else**, when there is
    /// nothing to draw: no legend, no plot — a legend keying series nobody can
    /// see is noise, and a grid over no data asserts a scale nobody supplied
    /// (`charts.md` §3.1, ADR-0200).
    public static List<Widget> of(List<Series> series, List<String> categories, Mode mode,
            int isolated, java.util.function.IntConsumer onIsolate, ChartOptions options) {

        var message = messageFor(options.status(), hasData(series));
        if (message != null) {
            return List.of(message);
        }
        var parts = new ArrayList<Widget>(2);
        parts.add(new ChartPlot(series, categories, switch (mode) {
            case LINE -> ChartPlot.Mode.LINE;
            case AREA -> ChartPlot.Mode.AREA;
            case BAR -> ChartPlot.Mode.BAR;
        }, options, isolated));
        if (series.size() > 1) {
            parts.add(new ChartLegend(series, isolated, onIsolate));
        }
        return List.copyOf(parts);
    }

    /// §3.2's inline data, read out of the `series` nodes the inflater built.
    ///
    /// The categories come from the **first** series' point names, because they
    /// are the x axis and a chart has one: a second series naming its points
    /// differently would be two x axes in one picture, which is the same mistake
    /// as two y axes and is refused the same way — by there being nowhere to put
    /// it.
    ///
    /// @param children what the inflater built, of which the `series` nodes are
    ///                 read and anything else ignored
    public static Read read(List<Widget> children) {
        var series = new ArrayList<Series>();
        var categories = new ArrayList<String>();
        for (var child : children) {
            if (!(child instanceof ChartSeries node)) {
                continue;
            }
            if (series.isEmpty()) {
                categories.addAll(node.labels());
            }
            series.add(node.toSeries(series.size()));
        }
        // Blank labels mean nobody named the points, and a row of empty labels
        // under an axis is worse than none.
        if (categories.stream().allMatch(String::isBlank)) {
            categories.clear();
        }
        return new Read(List.copyOf(series), List.copyOf(categories));
    }

    /// Whether there is anything to draw — a series with at least one point.
    ///
    /// A chart of three named series with no numbers in them is as empty as a
    /// chart of none, and it is the shape an application gets from a query that
    /// returned no rows.
    public static boolean hasData(List<Series> series) {
        // A **value**, not a point: a series of nothing but holes is as empty as
        // a series of no points, and it is what a query returning rows of nulls
        // produces (Series#valueCount).
        return series != null && series.stream().anyMatch(one -> one.valueCount() > 0);
    }

    /// The part a chart draws instead of its data, or **null** when it has some.
    ///
    /// Shared by all five charts, because "what a chart says when it has nothing
    /// to show" is one answer and four copies of it would drift.
    ///
    /// @param status  what the application said
    /// @param hasData whether the data it passed has anything in it
    public static Widget messageFor(ChartStatus status, boolean hasData) {
        var text = (status == null ? ChartStatus.READY : status).messageFor(hasData);
        if (text == null) {
            return null;
        }
        return new ChartMessage(text,
                (status == null ? ChartStatus.READY : status).styleClass(hasData));
    }

    /// What [#read] found.
    public record Read(List<Series> series, List<String> categories) {
    }
}
