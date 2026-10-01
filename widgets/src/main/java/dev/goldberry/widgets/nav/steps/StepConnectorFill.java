package dev.goldberry.widgets.nav.steps;

import java.util.List;
import java.util.Set;

import dev.goldberry.css.ComputedStyle;
import dev.goldberry.paint.Box;
import dev.goldberry.widget.Widget;
import dev.goldberry.widget.style.Paints;
import dev.goldberry.widget.style.Styled;

/// The filled part of a [StepConnector] — a **part**, CSS-selectable and not
/// constructible (ADR-0065).
///
/// It carries no state of its own: `step-connector.done step-connector-fill` is
/// how a stylesheet reaches it, so the one word the list writes is written once.
/// Its growth is a `transform` the stylesheet owns (ADR-0356).
record StepConnectorFill() implements Widget.Leaf, Styled, Paints {

    @Override
    public String cssType() {
        return "step-connector-fill";
    }

    @Override
    public Set<String> classes() {
        return Set.of();
    }

    @Override
    public Box render(ComputedStyle style, List<Box> children, Context context) {
        return Box.of().style(style);
    }
}
