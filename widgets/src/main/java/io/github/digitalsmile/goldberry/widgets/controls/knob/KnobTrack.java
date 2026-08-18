package io.github.digitalsmile.goldberry.widgets.controls.knob;

import io.github.digitalsmile.goldberry.css.ComputedStyle;
import io.github.digitalsmile.goldberry.layout.Box;
import io.github.digitalsmile.goldberry.widget.Paints;
import io.github.digitalsmile.goldberry.widget.Styled;
import io.github.digitalsmile.goldberry.widget.Widget;
import java.util.List;
import java.util.Set;

/// The 270° the value runs along — a **part** of [Knob], and the fifteenth in the
/// catalog.
///
/// It is the whole travel, drawn in the muted colour, with [KnobArc] nested
/// inside it drawing the part that is filled. Two nodes because two things need
/// two colours and one [ComputedStyle] carries one — the argument every part in
/// this catalog rests on ([ADR-0065]) — and *nested* rather than stacked because
/// the rings are concentric and §8's subset has no `position: absolute`. A child
/// at `100%` of a parent with no padding is exactly its parent's box, which is
/// stacking for as long as nothing has to overlap in two directions at once
/// ([ADR-0089]).
///
/// @param fraction how far round the travel the value is, `0..1`
/// @param disabled inherited from the knob, so the part is selectable without a
///                 descendant combinator ([ADR-0077])
record KnobTrack(double fraction, boolean disabled) implements Widget.Leaf, Styled, Paints {

    /// The ring's stroke, in logical pixels.
    ///
    /// §3's `knob` row pins the diameters and the arc and says nothing about the
    /// weight, so this is [io.github.digitalsmile.goldberry.widgets.controls.spinner.Spinner]'s answer for the same gap: Lucide's 2px stroke
    /// at 24 (§1.6), which is already the toolkit's line weight for anything drawn
    /// on that grid. Inventing a third number would be inventing a scale the
    /// design system does not have.
    static final double THICKNESS = 2;

    @Override
    public String cssType() {
        return "knob-track";
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
    public List<Widget> children() {
        return List.of(new KnobArc(fraction, disabled));
    }

    @Override
    public Box render(ComputedStyle style, List<Box> children, Context context) {
        return Box.of().style(style)
                .mark(new Box.Mark(Box.Mark.Kind.ARC, style.color(), THICKNESS,
                        Knob.ARC_START, Knob.ARC_SWEEP))
                .children(children.toArray(Box[]::new));
    }
}
