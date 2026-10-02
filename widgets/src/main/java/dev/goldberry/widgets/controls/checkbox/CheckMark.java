package dev.goldberry.widgets.controls.checkbox;

import java.util.List;
import java.util.Objects;
import java.util.Set;

import dev.goldberry.css.ComputedStyle;
import dev.goldberry.paint.Box;
import dev.goldberry.widget.Widget;
import dev.goldberry.widget.style.Paints;
import dev.goldberry.widget.style.Styled;

/// The tick or the dash inside a [CheckIndicator] — a **part**, and `RadioDot`'s
/// twin.
///
/// The design system animates a checkbox's tick and a radio's dot the same way:
/// they scale from 0.6 to 1 and fade in, while the glyph around them stays put.
/// A [Box.Mark] drawn onto the indicator's own box cannot do that — scaling the
/// box scales the 16px square with it — so the mark is a node of its own, with
/// a transform of its own, exactly as the dot is.
///
/// It is built in every state, including `UNCHECKED`. A node that appears only
/// while checked cannot transition, because the first frame of a newly built
/// element starts nothing, so the mark is always there and the stylesheet fades
/// it. Unchecked draws the tick at zero opacity: unchecked to checked is the
/// common transition, and the one where a shape swap would show. Going to
/// `MIXED` swaps to the dash instantly and then fades it in; the kind of mark is
/// not a property that animates.
///
/// @param state     which shape to draw; also what the parent's `:checked` and
///                  `:indeterminate` come from
/// @param disabled  inherited down from the checkbox
/// @param thickness the stroke width in logical pixels — the tick and the dash
///                  are stroked, unlike the radio's filled dot
record CheckMark(Checkbox.Value state, boolean disabled, double thickness) implements Widget.Leaf, Styled, Paints {

    CheckMark {
        Objects.requireNonNull(state, "state");
    }

    @Override
    public String cssType() {
        return "check-mark";
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
        var kind = state == Checkbox.Value.MIXED ? Box.Mark.Kind.DASH : Box.Mark.Kind.CHECK;
        return Box.of().style(style).mark(new Box.Mark(kind, style.color(), thickness));
    }
}
