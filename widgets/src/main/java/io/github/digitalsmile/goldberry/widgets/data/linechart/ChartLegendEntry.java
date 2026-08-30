package io.github.digitalsmile.goldberry.widgets.data.linechart;

import java.util.List;

import io.github.digitalsmile.goldberry.css.ComputedStyle;
import io.github.digitalsmile.goldberry.paint.Box;
import io.github.digitalsmile.goldberry.widget.Widget;
import io.github.digitalsmile.goldberry.widget.style.Paints;
import io.github.digitalsmile.goldberry.widget.style.Styled;
import io.github.digitalsmile.goldberry.widgets.data.SeriesPalette;
import io.github.digitalsmile.goldberry.widgets.text.Text;

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
/// ## Clicking it isolates its series
///
/// `CLICKED` and not `PRESSED`, which is the rule every control in the catalog
/// follows: a press dragged away and released elsewhere is a click the user
/// cancelled, and people rely on being able to do that (§7.1).
///
/// The **muted** state is a class rather than a colour set here, so the
/// stylesheet decides what "not currently shown" looks like — and so a theme can
/// make it something other than an opacity if it wants to. It is not `:disabled`:
/// a muted entry is the most clickable thing on the chart, because clicking it is
/// how you get the others back.
///
/// @param muted     whether another series is isolated, so this one is not drawn
/// @param onIsolate what a click reports, or null for a legend that is only a key
record ChartLegendEntry(int slot, String name, boolean muted, java.util.function.IntConsumer onIsolate)
        implements Widget.Leaf, Styled, Paints, io.github.digitalsmile.goldberry.input.handler.Handles {

    ChartLegendEntry(int slot, String name) {
        this(slot, name, false, null);
    }

    @Override
    public String cssType() {
        return "chart-legend-entry";
    }

    /// `muted` when another series is isolated, and `interactive` when clicking
    /// this entry would do anything.
    ///
    /// Two classes rather than two type rules, because whether a legend is a
    /// control depends on the chart it is in: `donut-chart`'s entries are a key
    /// and nothing else, and a `cursor: pointer` on the type would promise them
    /// an affordance they do not have.
    @Override
    public java.util.Set<String> classes() {
        var classes = new java.util.LinkedHashSet<String>(2);
        if (muted) {
            classes.add("muted");
        }
        if (onIsolate != null) {
            classes.add("interactive");
        }
        return classes;
    }

    @Override
    public List<Widget> children() {
        return List.of(new Swatch(slot), new Text(name));
    }

    @Override
    public void onPointer(io.github.digitalsmile.goldberry.input.event.PointerEvent event) {
        if (onIsolate == null
                || event.kind() != io.github.digitalsmile.goldberry.input.event.PointerEvent.Kind.CLICKED) {
            return;
        }
        onIsolate.accept(slot);
        // Consumed, because a click that isolated a series and then also reached
        // whatever the chart is sitting in -- a `card` that selects, a row that
        // opens -- would be one gesture doing two things.
        event.consume();
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
