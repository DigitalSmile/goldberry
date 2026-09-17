package io.github.digitalsmile.goldberry.widgets.nav.steps;

import java.util.List;
import java.util.Set;

import io.github.digitalsmile.goldberry.css.ComputedStyle;
import io.github.digitalsmile.goldberry.paint.Box;
import io.github.digitalsmile.goldberry.widget.Widget;
import io.github.digitalsmile.goldberry.widget.style.Paints;
import io.github.digitalsmile.goldberry.widget.style.Styled;

/// The line between two steps — a **part**, CSS-selectable and not
/// constructible (ADR-0065).
///
/// Filled when the step before it is done, so the line is drawn from where you
/// have been and stops at where you are: `step-connector.done` is the whole of
/// how a stylesheet says so. It is a box with a background and nothing in it,
/// and the design-system's "connector fill `transform: scaleX` base" is a
/// transition the stylesheet owns.
///
/// @param done whether the step before it is done
record StepConnector(boolean done) implements Widget.Leaf, Styled, Paints {

    @Override
    public String cssType() {
        return "step-connector";
    }

    @Override
    public Set<String> classes() {
        return done ? Set.of(StepState.DONE.word()) : Set.of();
    }

    @Override
    public Box render(ComputedStyle style, List<Box> children, Context context) {
        return Box.of().style(style);
    }
}
