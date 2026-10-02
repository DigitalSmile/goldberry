package dev.goldberry.widgets.controls.radio;

import java.util.List;
import java.util.Set;

import dev.goldberry.css.ComputedStyle;
import dev.goldberry.paint.Box;
import dev.goldberry.widget.Widget;
import dev.goldberry.widget.style.Paints;
import dev.goldberry.widget.style.Styled;

/// The dot inside a [RadioIndicator] — a **part**, and a node of its own
/// because it moves on its own.
///
/// The design system scales the dot from 0.6 to 1 and fades it in while the
/// ring stays put. A [Box.Mark] is drawn *onto* whatever box carries it, so
/// scaling that box scales the whole 16px circle — the ring grows with the dot,
/// which is not the animation and looks like a bug. The dot needs a transform of
/// its own, a transform belongs to a `ComputedStyle`, and a `ComputedStyle`
/// belongs to an element. So the dot is an element: two things that move
/// independently are two cascade nodes.
///
/// It is always here, which is the point. A node that only exists while
/// `:checked` cannot transition: there is no
/// previous style to move from, and the first frame of a newly built element
/// starts nothing by design. So the dot is built in every state and the
/// stylesheet fades and scales it — `opacity: 0; transform: scale(0.6)` at rest,
/// `1` and `scale(1)` under `radio-indicator:checked`. Unchecked therefore costs
/// one fully transparent box rather than nothing, which is the price of the
/// specified animation.
///
/// The mark is drawn in `style.color()`, which **inherits** — so
/// `radio-indicator:checked { color: … }` still moves it and no rule has to name
/// this node to recolour it.
///
/// @param disabled inherited down from the radio, so a stylesheet can reach a
///                 disabled dot without a two-step descendant selector
record RadioDot(boolean disabled) implements Widget.Leaf, Styled, Paints {

    /// A filled mark ignores its stroke width, and [Box.Mark] refuses a zero —
    /// a stroked mark with no width would be an invisible tick, and the
    /// constructor would rather say so than draw nothing.
    private static final double FILLED = 1;

    @Override
    public String cssType() {
        return "radio-dot";
    }

    @Override
    public Set<String> classes() {
        return Set.of();
    }

    @Override
    public boolean isDisabled() {
        return disabled;
    }

    @Override
    public Box render(ComputedStyle style, List<Box> children, Context context) {
        return Box.of().style(style).mark(new Box.Mark(Box.Mark.Kind.DOT, style.color(), FILLED));
    }
}
