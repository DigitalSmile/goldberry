package dev.goldberry.widgets.controls.knob;

import dev.goldberry.css.ComputedStyle;
import dev.goldberry.paint.Box;
import dev.goldberry.widget.style.Paints;
import dev.goldberry.widget.style.Styled;
import dev.goldberry.widget.Widget;
import java.util.List;
import java.util.Set;

/// The 270° a [Knob]'s value runs along, styled as `knob-track`.
///
/// It is the whole travel, drawn in the muted colour, with [KnobArc] nested
/// inside it drawing the part that is filled. Two nodes because two things need
/// two colours and one [ComputedStyle] carries one — a part exists to be styled
/// on its own — and *nested* rather than stacked because the rings are
/// concentric and the stylesheet subset has no `position: absolute`. A child at
/// `100%` of a parent with no padding is exactly its parent's box, which is
/// stacking for as long as nothing has to overlap in two directions at once.
///
/// Read more: [Values and progress](https://goldberry.dev/docs/components/values.html#knob).
///
/// @param fraction how far round the travel the value is, `0..1`
/// @param disabled inherited from the knob, so the part is selectable without a
///                 descendant combinator
record KnobTrack(double fraction, boolean disabled) implements Widget.Leaf, Styled, Paints {

    /// The ring's stroke, in logical pixels.
    ///
    /// The design system pins the knob's diameters and its arc and says nothing
    /// about the weight, so this is
    /// [dev.goldberry.widgets.controls.spinner.Spinner]'s answer for the same
    /// gap: the icon set's 2px stroke at 24, which is already the toolkit's line
    /// weight for anything drawn on that grid. Inventing a third number would be
    /// inventing a scale the design system does not have.
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
