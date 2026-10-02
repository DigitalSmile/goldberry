package dev.goldberry.widgets.nav.steps;

import java.util.List;
import java.util.Set;

import dev.goldberry.css.ComputedStyle;
import dev.goldberry.paint.Box;
import dev.goldberry.widget.Widget;
import dev.goldberry.widget.style.Paints;
import dev.goldberry.widget.style.Styled;

/// The line between two steps — a **part**, CSS-selectable and not
/// constructible.
///
/// Filled when the step before it is done, so the line is drawn from where you
/// have been and stops at where you are: `step-connector.done` is the whole of
/// how a stylesheet says so.
///
/// ## A track with a fill in it
///
/// The connector is the unfilled track, and its one child is a
/// [StepConnectorFill] that covers it. The fill grows through a `scaleX`
/// transition on that child: it is `scaleX(0)` about its start
/// edge until the connector is done and `scaleX(1)` after, so the line grows from
/// the step you left towards the one you reached. The fill is present
/// in every state for the reason `check-mark` is — a node built already done has
/// no previous style to move from, and would snap.
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
    public List<Widget> children() {
        return List.of(new StepConnectorFill());
    }

    @Override
    public Box render(ComputedStyle style, List<Box> children, Context context) {
        return Box.of().style(style).children(children.toArray(Box[]::new));
    }
}
