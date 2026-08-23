package io.github.digitalsmile.goldberry.widgets.data.linechart;

import io.github.digitalsmile.goldberry.css.ComputedStyle;
import io.github.digitalsmile.goldberry.paint.Box;
import io.github.digitalsmile.goldberry.widget.style.Paints;
import io.github.digitalsmile.goldberry.widget.style.Styled;
import io.github.digitalsmile.goldberry.widget.Widget;
import io.github.digitalsmile.goldberry.widgets.data.SeriesPalette;
import io.github.digitalsmile.goldberry.widgets.text.Text;
import java.util.List;

/// One row of a [ChartLegend]: a swatch in the series' colour, and its name.
///
/// The **swatch** reads the palette and the **label** does not, which is §14's
/// rule about text: a colour beside a word carries the identity, and the word
/// itself stays in the ordinary ink. A legend whose text was drawn in the series
/// colour would be a legend that fails a contrast check the moment somebody picks
/// a pale slot — and it reads as decoration rather than as a key.
///
/// The slot is read here rather than passed in, so the token resolves against
/// **this node**. Custom properties inherit, so `#revenue { --gb-chart-1: … }` on
/// the chart reaches the swatch and the line alike — one rule, both halves
/// (ADR-0195).
record ChartLegendEntry(int slot, String name) implements Widget.Leaf, Styled, Paints {

    @Override
    public String cssType() {
        return "chart-legend-entry";
    }

    @Override
    public List<Widget> children() {
        return List.of(new Swatch(slot), new Text(name));
    }

    @Override
    public Box render(ComputedStyle style, List<Box> children, Context context) {
        return Box.of().style(style).children(children.toArray(Box[]::new));
    }

    /// The colour chip. A part of a part, and the only node in the chart whose
    /// whole job is to be one colour.
    record Swatch(int slot) implements Widget.Leaf, Styled, Paints {

        @Override
        public String cssType() {
            return "chart-legend-swatch";
        }

        @Override
        public Box render(ComputedStyle style, List<Box> children, Context context) {
            // The palette wins over `background`, because a swatch that a
            // stylesheet could recolour independently of its line would be a
            // legend that lies. The way to change it is the token, which changes
            // both.
            return Box.of().style(style).background(SeriesPalette.of(context, slot));
        }
    }
}
