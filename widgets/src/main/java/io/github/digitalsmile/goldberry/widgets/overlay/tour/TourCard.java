package io.github.digitalsmile.goldberry.widgets.overlay.tour;

import io.github.digitalsmile.goldberry.css.ComputedStyle;
import io.github.digitalsmile.goldberry.layout.Box;
import io.github.digitalsmile.goldberry.natives.yoga.FlexDirection;
import io.github.digitalsmile.goldberry.widget.Paints;
import io.github.digitalsmile.goldberry.widget.Styled;
import io.github.digitalsmile.goldberry.widget.Widget;
import java.util.List;
import java.util.Set;

/// The panel a [TourStop] shows beside its target — a **part**, styled by
/// `controls.css` and not constructible.
///
/// Not a `popover`. §5 calls a tour "a guided sequence of `popover`s" and the
/// word is doing less work than it looks: `popover` is the *panel* half of an
/// anchored floating thing, and its opening half — measure, flip, shift, open a
/// window, light-dismiss — is precisely what a tour must not do
/// ([ADR-0104](../../../../../../../../book/src/adr/0104-a-popup-is-measured-then-placed.md)).
/// A tour's card lives inside the window, over a veil that is also inside it, and
/// dismisses on its own buttons rather than on an outside click. Reusing the
/// widget would have meant reusing the surface and the radius, which is what a
/// stylesheet is for.
record TourCard(Widget content) implements Widget.Leaf, Styled, Paints {

    @Override
    public String cssType() {
        return "tour-card";
    }

    @Override
    public Set<String> classes() {
        return Set.of();
    }

    @Override
    public List<Widget> children() {
        return List.of(content);
    }

    @Override
    public Box render(ComputedStyle style, List<Box> children, Context context) {
        return Box.of().style(style).children(children.toArray(Box[]::new))
                .direction(FlexDirection.COLUMN);
    }
}
