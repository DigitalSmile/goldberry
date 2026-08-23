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
public record ChartLegend(List<Series> series) implements Widget.Leaf, Styled, Paints {

    @Override
    public String cssType() {
        return "chart-legend";
    }

    @Override
    public List<Widget> children() {
        var entries = new ArrayList<Widget>(series.size());
        for (var i = 0; i < series.size(); i++) {
            entries.add(new ChartLegendEntry(i, series.get(i).name()));
        }
        return List.copyOf(entries);
    }

    @Override
    public Box render(ComputedStyle style, List<Box> children, Context context) {
        return Box.of().style(style).children(children.toArray(Box[]::new));
    }
}
