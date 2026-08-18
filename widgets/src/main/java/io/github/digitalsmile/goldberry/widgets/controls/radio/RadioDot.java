package io.github.digitalsmile.goldberry.widgets.controls.radio;

import io.github.digitalsmile.goldberry.css.ComputedStyle;
import io.github.digitalsmile.goldberry.layout.Box;
import io.github.digitalsmile.goldberry.widget.Paints;
import io.github.digitalsmile.goldberry.widget.Styled;
import io.github.digitalsmile.goldberry.widget.Widget;
import java.util.List;
import java.util.Set;

/// The dot inside a [RadioIndicator] — a **part**, and the reason there are three
/// of them rather than two.
///
/// ## Why the dot is a node and not just a mark on the glyph
///
/// `docs/design-system.md` §3.1 specifies the radio's dot as "scale 0.6→1 +
/// `opacity`, base". A [Box.Mark] is drawn *onto* whatever box carries it, so
/// scaling that box scales the whole 16px circle — the ring grows with the dot,
/// which is not the animation and looks like a bug. The dot needs a transform of
/// its own, a transform belongs to a `ComputedStyle`, and a `ComputedStyle`
/// belongs to an element. So the dot is an element.
///
/// [ADR-0065](../../../../../../../../book/src/adr/0065-a-part-is-styleable-and-not-constructible.md)
/// asked that the part argument be re-made rather than reused each time, and this
/// is the third asking. It holds again, for a reason the first two did not have:
/// not "two surfaces need two backgrounds" but **two things need to move
/// independently**. That is the same argument in the animation dimension, and it
/// is what §1.7's whitelist is for.
///
/// ## It is always here, which is the point
///
/// A node that only exists while `:checked` cannot transition: there is no
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
        return Box.of().style(style)
                .mark(new Box.Mark(Box.Mark.Kind.DOT, style.color(), FILLED));
    }
}
