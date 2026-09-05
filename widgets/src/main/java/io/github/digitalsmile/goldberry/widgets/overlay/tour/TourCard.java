package io.github.digitalsmile.goldberry.widgets.overlay.tour;

import java.util.List;
import java.util.Set;

import io.github.digitalsmile.goldberry.css.ComputedStyle;
import io.github.digitalsmile.goldberry.natives.yoga.style.FlexDirection;
import io.github.digitalsmile.goldberry.paint.Box;
import io.github.digitalsmile.goldberry.widget.Widget;
import io.github.digitalsmile.goldberry.widget.style.Paints;
import io.github.digitalsmile.goldberry.widget.style.Styled;

/// The panel a [TourStop] shows beside its target — a **part**, styled by
/// `controls.css` and not constructible.
///
/// Not a `popover`. §5 calls a tour "a guided sequence of `popover`s" and the
/// word is doing less work than it looks: `popover` is the *panel* half of an
/// anchored floating thing, and its opening half — measure, flip, shift, open a
/// window, light-dismiss — is precisely what a tour must not do
/// (ADR-0104).
/// A tour's card lives inside the window, over a veil that is also inside it, and
/// dismisses on its own buttons rather than on an outside click. Reusing the
/// widget would have meant reusing the surface and the radius, which is what a
/// stylesheet is for.
///
/// ## It says how tall it came out
///
/// [TourStop] decides whether the card fits below its target, and used a
/// **constant** to do it — 132, "enough for a title, three lines of body and the
/// buttons". Being wrong put a card above its target when it would have fitted
/// below, which is a stop pointing the wrong way for a reason nothing said.
///
/// So it reports, through [Measured], and [TourState] banks it exactly as it
/// banks the window's own rectangle. `Measured`'s third rule holds **by
/// construction**: the card's width is fixed at [TourStop]'s 280 and its content
/// is the stop's own text, so its height does not depend on where it is placed —
/// the number is stable under the thing it causes ([ADR-0268]).
///
/// @param content    the title, the body, the counter and the buttons
/// @param onMeasured told the card's height, or null when nobody is banking it
record TourCard(Widget content, java.util.function.DoubleConsumer onMeasured)
        implements Widget.Leaf, Styled, Paints, io.github.digitalsmile.goldberry.input.handler.Measured {

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
    public void measured(
            io.github.digitalsmile.goldberry.input.hit.Extent bounds,
            io.github.digitalsmile.goldberry.input.hit.Extent part) {

        if (onMeasured != null) {
            onMeasured.accept(bounds.height());
        }
    }

    @Override
    public Box render(ComputedStyle style, List<Box> children, Context context) {
        return Box.of().style(style).children(children.toArray(Box[]::new)).direction(FlexDirection.COLUMN);
    }
}
