package io.github.digitalsmile.goldberry.widgets.nav.steps;

import java.util.List;
import java.util.Set;

import org.jspecify.annotations.Nullable;

import io.github.digitalsmile.goldberry.css.ComputedStyle;
import io.github.digitalsmile.goldberry.paint.Box;
import io.github.digitalsmile.goldberry.widget.Widget;
import io.github.digitalsmile.goldberry.widget.style.Paints;
import io.github.digitalsmile.goldberry.widget.style.Styled;

/// A step's words: the label and, under it, the description — a **part**.
///
/// A column of two text parts rather than two children of the step, so the
/// marker and the words are two things side by side and the description hangs
/// under the label rather than after the disc.
///
/// @param label       the step's name
/// @param description the second line, or null for none
record StepBody(String label, @Nullable String description) implements Widget.Leaf, Styled, Paints {

    @Override
    public String cssType() {
        return "step-body";
    }

    @Override
    public Set<String> classes() {
        return Set.of();
    }

    @Override
    public List<Widget> children() {
        return description == null
                ? List.of(new StepText(label, "step-label"))
                : List.of(new StepText(label, "step-label"), new StepText(description, "step-description"));
    }

    @Override
    public Box render(ComputedStyle style, List<Box> children, Context context) {
        return Box.of().style(style).children(children.toArray(Box[]::new));
    }
}
