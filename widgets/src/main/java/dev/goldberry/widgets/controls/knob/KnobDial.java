package dev.goldberry.widgets.controls.knob;

import java.util.List;
import java.util.Set;

import dev.goldberry.css.ComputedStyle;
import dev.goldberry.paint.Box;
import dev.goldberry.widget.Widget;
import dev.goldberry.widget.style.Paints;
import dev.goldberry.widget.style.Styled;

/// The body you grab — the disc inside a [Knob]'s rings, styled as `knob-dial`.
///
/// The dial is inset from the rings: they keep the full 32 px and this sits
/// inside them, with the window showing through the gap. A ring needs something
/// behind it that is not the thing it is measuring — stroked at the edge of the
/// dial's own box, the muted track would run across the body at a contrast too
/// low to read, and the 270° of travel a user is supposed to see would be
/// invisible.
///
/// ## It carries the pointer
///
/// An arc alone reads as a gauge — you can see how full it is, and there is
/// nothing on the dial that turns. So the dial carries a
/// [Box.Mark.Kind#POINTER], a radial line at the value's own angle, and the
/// control reads as a knob rather than as a ring with a disc in it.
///
/// It is a mark on this node rather than a part of its own: a part is a node
/// because two things must be styled or **moved** independently, and the pointer
/// is neither — it is drawn in one colour at one angle, and the angle is not a
/// `transform` because a mark's geometry is a painter argument.
///
/// Read more: [Values and progress](https://goldberry.dev/docs/components/values.html#knob).
///
/// @param fraction how far round the travel the value is, `0..1` — the angle the
///                 pointer is drawn at
/// @param disabled inherited from the knob, so the part is selectable without a
///                 descendant combinator
record KnobDial(double fraction, boolean disabled) implements Widget.Leaf, Styled, Paints {

    @Override
    public String cssType() {
        return "knob-dial";
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
        return Box.of()
                .style(style)
                .mark(new Box.Mark(
                        Box.Mark.Kind.POINTER, style.color(), KnobTrack.THICKNESS, Knob.angleAt(fraction), 0));
    }
}
