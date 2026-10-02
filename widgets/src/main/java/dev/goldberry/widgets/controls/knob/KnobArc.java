package dev.goldberry.widgets.controls.knob;

import java.util.List;
import java.util.Set;

import dev.goldberry.css.ComputedStyle;
import dev.goldberry.paint.Box;
import dev.goldberry.widget.Widget;
import dev.goldberry.widget.style.Paints;
import dev.goldberry.widget.style.Styled;

/// The filled part of a knob's arc — the part of [KnobTrack] the value has
/// covered, styled as `knob-arc`.
///
/// A mark whose **geometry is the value**. A checkbox's tick and a radio's dot
/// are the same drawing every time and only appear or not; a spinner's ring is a
/// fixed three quarters that a `transform` turns. This one is a sweep of
/// `fraction × 270°`, which is why [Box.Mark] carries angles.
///
/// A fraction of zero sweeps zero, and `Arc.addTo` draws nothing for a zero
/// sweep — so a knob at its minimum has no arc rather than a one-pixel stub,
/// without anything here testing for it.
///
/// Read more: [Values and progress](https://goldberry.dev/docs/components/values.html#knob).
///
/// @param fraction how far round the travel the value is, `0..1`
/// @param disabled inherited from the knob, so the part is selectable without a
///                 descendant combinator
record KnobArc(double fraction, boolean disabled) implements Widget.Leaf, Styled, Paints {

    @Override
    public String cssType() {
        return "knob-arc";
    }

    /// [KnobDial], which is inset by this node's padding — see that part for why
    /// the body cannot be the same box as the rings.
    @Override
    public List<Widget> children() {
        return List.of(new KnobDial(fraction, disabled));
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
                        Box.Mark.Kind.ARC,
                        style.color(),
                        KnobTrack.THICKNESS,
                        Knob.ARC_START,
                        Knob.ARC_SWEEP * clamp(fraction)))
                .children(children.toArray(Box[]::new));
    }

    private static double clamp(double value) {
        return value < 0 ? 0 : value > 1 ? 1 : value;
    }
}
