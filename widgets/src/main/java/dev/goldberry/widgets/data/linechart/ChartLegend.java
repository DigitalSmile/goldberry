package dev.goldberry.widgets.data.linechart;

import java.util.ArrayList;
import java.util.List;

import org.jspecify.annotations.Nullable;

import dev.goldberry.css.ComputedStyle;
import dev.goldberry.paint.Box;
import dev.goldberry.widget.Widget;
import dev.goldberry.widget.style.Paints;
import dev.goldberry.widget.style.Styled;
import dev.goldberry.widgets.data.Series;

/// Which line is which — the `chart-legend` part every chart with two or more
/// series carries.
///
/// **Present for two series and absent for one**, which is a rule rather than an
/// option: with one line the title names it and a legend box would be a second
/// label for the same thing, and with two the colour is the only thing telling
/// them apart, so identity must not be colour alone.
///
/// Real widgets rather than something the painter draws. A legend is text and a
/// swatch — the two things the toolkit is already good at — and making them nodes
/// means a stylesheet reaches them, the text is shaped by the same cache as
/// everything else, and the entries wrap when the chart is narrow because the
/// stylesheet has `flex-wrap`. A legend drawn inside the canvas would have
/// re-implemented all three.
///
/// ## And it is a control, not a caption
///
/// Clicking an entry **isolates** its series; clicking it again puts them all
/// back. It is the interaction a dashboard's users reach for first, and it is
/// the second thing the legend being real widgets paid for: the click is an
/// ordinary pointer handler on an ordinary node, with the cursor and the hover
/// state a stylesheet already knows how to draw.
///
/// The entries that are *not* isolated are dimmed rather than removed. A legend
/// that dropped them would change width as you clicked it, and the way back
/// would disappear along with them.
///
/// Read more: [Charts](https://goldberry.dev/docs/components/charts.html#what-the-five-share).
///
/// @param series    every series, isolated or not — the order is the colour
///                  order
/// @param isolated  the one being shown alone, or -1 for all of them
/// @param onIsolate what a click reports, or null for a legend that is only a
///                  key — which is what `donut-chart` builds
public record ChartLegend(List<Series> series, int isolated, java.util.function.@Nullable IntConsumer onIsolate)
        implements Widget.Leaf, Styled, Paints {

    /// A legend nobody can click, which is every legend that came before the
    /// interaction layer.
    public ChartLegend(List<Series> series) {
        this(series, -1, null);
    }

    @Override
    public String cssType() {
        return "chart-legend";
    }

    @Override
    public List<Widget> children() {
        var entries = new ArrayList<Widget>(series.size());
        for (var i = 0; i < series.size(); i++) {
            // Muted when *another* series is isolated: the isolated one is the
            // only thing on the plot, so it is the only entry drawn at full
            // strength.
            entries.add(new ChartLegendEntry(i, series.get(i).name(), isolated >= 0 && isolated != i, onIsolate));
        }
        return List.copyOf(entries);
    }

    @Override
    public Box render(ComputedStyle style, List<Box> children, Context context) {
        return Box.of().style(style).children(children.toArray(Box[]::new));
    }
}
