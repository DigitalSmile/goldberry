package io.github.digitalsmile.goldberry.widgets.controls.knob;

import io.github.digitalsmile.goldberry.css.ComputedStyle;
import io.github.digitalsmile.goldberry.layout.Box;
import io.github.digitalsmile.goldberry.widget.Paints;
import io.github.digitalsmile.goldberry.widget.Styled;
import io.github.digitalsmile.goldberry.widget.Widget;
import java.util.List;
import java.util.Set;

/// The body you grab — a **part** of [Knob], and the seventeenth in the catalog.
///
/// It exists because the first drawing of this control did not work, and the
/// golden image is the only thing that could have said so. The dial was
/// [Knob]'s own `background` and the rings were stroked at the edge of the same
/// box, so the track ran **across the body** — `--gb-border` on `--gb-knob-bg` is
/// `--nord3` on `--nord2`, about 1.2:1, and the 270° of travel a user is supposed
/// to read was invisible. Every value assertion passed.
///
/// So the dial is inset instead: the rings keep the full 32px and this sits
/// inside them, with the window showing through the gap. That is what a rotary
/// control looks like everywhere, and the reason is the one this part is —
/// **a ring needs something behind it that is not the thing it is measuring**
/// ([ADR-0089]).
///
/// ## It carries the pointer
///
/// §3's row asks a knob for an "arc indicator" and `core-widgets.md` §3 for a
/// rotary control; between them they say what the *value* is and never say which
/// way the thing is **pointing**. An arc alone reads as a gauge — you can see how
/// full it is, and there is nothing on the dial that turns. So the dial carries a
/// [Box.Mark.Kind#POINTER], a radial line at the value's own angle, and the
/// control reads as a knob rather than as a ring with a disc in it.
///
/// It is a mark on this node rather than a part of its own, which is the first
/// time that answer has been the right one since `CheckMark`: a part is a node
/// because two things must be styled or **moved** independently ([ADR-0073]), and
/// the pointer is neither — it is drawn in one colour at one angle, and the angle
/// is not a `transform` because a mark's geometry is a painter argument
/// ([ADR-0089]).
///
/// @param fraction how far round the travel the value is, `0..1` — the angle the
///                 pointer is drawn at
/// @param disabled inherited from the knob, so the part is selectable without a
///                 descendant combinator ([ADR-0077])
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
        return Box.of().style(style)
                .mark(new Box.Mark(Box.Mark.Kind.POINTER, style.color(), KnobTrack.THICKNESS,
                        Knob.angleAt(fraction), 0));
    }
}
