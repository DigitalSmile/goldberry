package io.github.digitalsmile.goldberry.widgets.data.linechart;

import io.github.digitalsmile.goldberry.css.ComputedStyle;
import io.github.digitalsmile.goldberry.paint.Box;
import io.github.digitalsmile.goldberry.widget.style.Paints;
import io.github.digitalsmile.goldberry.widget.style.Styled;
import io.github.digitalsmile.goldberry.widget.Widget;
import io.github.digitalsmile.goldberry.widgets.data.Series;
import java.util.ArrayList;
import java.util.List;

/// Which line is which — a **part** of [LineChart].
///
/// **Present for two series and absent for one**, which is a rule rather than an
/// option: with one line the title names it and a legend box would be a second
/// label for the same thing, and with two the colour is the only thing telling
/// them apart, so identity must not be colour alone.
///
/// Real widgets rather than something the painter draws. A legend is text and a
/// swatch — the two things the toolkit is already good at — and making them nodes
/// means a stylesheet reaches them, the text is shaped by the same cache as
/// everything else, and the entries wrap when the chart is narrow because §8's
/// subset has `flex-wrap` now (ADR-0192). A legend drawn inside the canvas would
/// have re-implemented all three.
///
/// ## And it is a control, not a caption
///
/// Clicking an entry **isolates** its series; clicking it again puts them all
/// back. `charts.md` §3.1 calls it "the one interaction Grafana users reach for
/// first", and it is the second thing the legend being real widgets paid for:
/// the click is an ordinary pointer handler on an ordinary node, with the
/// cursor and the hover state a stylesheet already knows how to draw.
///
/// The entries that are *not* isolated are dimmed rather than removed. A legend
/// that dropped them would change width as you clicked it, and the way back
/// would disappear along with them.
///
/// @param series    every series, isolated or not — the order is the colour
///                  order (ADR-0194)
/// @param isolated  the one being shown alone, or -1 for all of them
/// @param onIsolate what a click reports, or null for a legend that is only a
///                  key — which is what `donut-chart` builds
public record ChartLegend(
        List<Series> series, int isolated, java.util.function.IntConsumer onIsolate)
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
            entries.add(new ChartLegendEntry(i, series.get(i).name(),
                    isolated >= 0 && isolated != i, onIsolate));
        }
        return List.copyOf(entries);
    }

    @Override
    public Box render(ComputedStyle style, List<Box> children, Context context) {
        return Box.of().style(style).children(children.toArray(Box[]::new));
    }
}
