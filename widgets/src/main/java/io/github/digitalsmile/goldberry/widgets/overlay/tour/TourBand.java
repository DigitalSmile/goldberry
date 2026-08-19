package io.github.digitalsmile.goldberry.widgets.overlay.tour;

import io.github.digitalsmile.goldberry.css.ComputedStyle;
import io.github.digitalsmile.goldberry.input.Handles;
import io.github.digitalsmile.goldberry.input.PointerEvent;
import io.github.digitalsmile.goldberry.layout.Box;
import io.github.digitalsmile.goldberry.widget.Paints;
import io.github.digitalsmile.goldberry.widget.Styled;
import io.github.digitalsmile.goldberry.widget.Widget;
import java.util.Set;

/// One of a [TourVeil]'s four rectangles — a **part**, so it is styleable and not
/// constructible ([ADR-0065]).
///
/// It swallows the pointer. That is what makes a tour modal without anything
/// declaring it so: everything the veil covers is unreachable because the veil is
/// over it, and the one thing it does not cover is the thing the stop is about.
record TourBand() implements Widget.Leaf, Styled, Paints, Handles {

    @Override
    public String cssType() {
        return "tour-band";
    }

    @Override
    public Set<String> classes() {
        return Set.of();
    }

    @Override
    public void onPointer(PointerEvent event) {
        // Consumed rather than ignored: an unconsumed press falls through to
        // whatever is behind, and "behind" here is the application the tour is
        // explaining. A click on the dim is a click on nothing.
        event.consume();
    }

    @Override
    public Box render(ComputedStyle style, java.util.List<Box> children, Context context) {
        return Box.of().style(style);
    }
}
