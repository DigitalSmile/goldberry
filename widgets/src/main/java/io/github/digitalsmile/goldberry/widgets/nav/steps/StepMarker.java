package io.github.digitalsmile.goldberry.widgets.nav.steps;

import java.util.List;
import java.util.Set;

import io.github.digitalsmile.goldberry.css.ComputedStyle;
import io.github.digitalsmile.goldberry.paint.Box;
import io.github.digitalsmile.goldberry.widget.Widget;
import io.github.digitalsmile.goldberry.widget.style.Paints;
import io.github.digitalsmile.goldberry.widget.style.Styled;

/// The disc before a step's label — a **part**, CSS-selectable and not
/// constructible (ADR-0065).
///
/// What is in it depends on the state, and that is the one thing colour must
/// not be left to carry alone: a done step has a tick, a failed one a cross,
/// and the other two show their number. The state's word is a class here as
/// well as on the step, so a stylesheet can fill the disc without a descendant
/// selector reaching through `step.done`.
///
/// @param index which step this is, zero-based; drawn as `index + 1`
/// @param state what to put in the disc
record StepMarker(int index, StepState state) implements Widget.Leaf, Styled, Paints {

    @Override
    public String cssType() {
        return "step-marker";
    }

    @Override
    public Set<String> classes() {
        return Set.of(state.word());
    }

    @Override
    public Box render(ComputedStyle style, List<Box> children, Context context) {
        return switch (state) {
            case DONE -> Box.of().style(style).mark(new Box.Mark(Box.Mark.Kind.CHECK, style.color(), 2));
            case ERROR -> Box.of().style(style).mark(new Box.Mark(Box.Mark.Kind.CROSS, style.color(), 2));
            // The number is a child of the disc rather than the disc's own
            // text, so the stylesheet's centring applies to it.
            case CURRENT, UPCOMING ->
                Box.of()
                        .style(style)
                        .children(Box.text(context.paragraph(style, Integer.toString(index + 1)), style.color()));
        };
    }
}
