package dev.goldberry.widgets.overlay.tour;

import java.util.List;
import java.util.Set;
import java.util.function.DoubleConsumer;

import org.jspecify.annotations.Nullable;

import dev.goldberry.css.ComputedStyle;
import dev.goldberry.layout.FlexDirection;
import dev.goldberry.paint.Box;
import dev.goldberry.widget.Widget;
import dev.goldberry.widget.style.Paints;
import dev.goldberry.widget.style.Styled;

/// The panel a [TourStop] shows beside its target — a **part**, styled by
/// `controls.css` and not constructible.
///
/// Not a `popover`, though a tour is a guided sequence of popover-shaped cards:
/// `popover` is the *panel* half of an
/// anchored floating thing, and its opening half — measure, flip, shift, open a
/// window, light-dismiss — is precisely what a tour must not do.
/// A tour's card lives inside the window, over a veil that is also inside it, and
/// dismisses on its own buttons rather than on an outside click. Reusing the
/// widget would have meant reusing the surface and the radius, which is what a
/// stylesheet is for.
///
/// ## It says how tall it came out
///
/// [TourStop] decides whether the card fits below its target, and a guess at
/// the height — 132, enough for a title, three lines of body and the buttons —
/// is good only for the first frame: a guess that is wrong puts a card above
/// its target when it would have fitted below, which is a stop pointing the
/// wrong way for a reason nothing said.
///
/// So it reports, through [Measured], and [TourState] banks it exactly as it
/// banks the window's own rectangle. `Measured`'s third rule holds **by
/// construction**: the card's width is fixed at [TourStop]'s 280 and its content
/// is the stop's own text, so its height does not depend on where it is placed —
/// the number is stable under the thing it causes.
///
/// @param content    the title, the body, the counter and the buttons
/// @param onMeasured told the card's height, or null when nobody is banking it
record TourCard(Widget content, @Nullable DoubleConsumer onMeasured)
        implements Widget.Leaf, Styled, Paints, dev.goldberry.input.handler.Measured {

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
    public void measured(dev.goldberry.input.hit.Extent bounds, dev.goldberry.input.hit.Extent part) {

        if (onMeasured != null) {
            onMeasured.accept(bounds.height());
        }
    }

    @Override
    public Box render(ComputedStyle style, List<Box> children, Context context) {
        return Box.of().style(style).children(children.toArray(Box[]::new)).direction(FlexDirection.COLUMN);
    }
}
