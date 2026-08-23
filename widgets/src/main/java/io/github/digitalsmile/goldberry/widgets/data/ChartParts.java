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

    /// The plot, plus a legend when it is needed.
    ///
    /// **Two series or more.** With one line the title names it and a legend box
    /// repeats it; with two, colour is the only thing telling them apart, so
    /// identity must never be colour alone.
    public static List<Widget> of(List<Series> series, List<String> categories, Mode mode) {
        var parts = new ArrayList<Widget>(2);
        parts.add(new ChartPlot(series, categories, switch (mode) {
            case LINE -> ChartPlot.Mode.LINE;
            case AREA -> ChartPlot.Mode.AREA;
            case BAR -> ChartPlot.Mode.BAR;
        }));
        if (series.size() > 1) {
            parts.add(new ChartLegend(series));
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

    /// What [#read] found.
    public record Read(List<Series> series, List<String> categories) {
    }
}
