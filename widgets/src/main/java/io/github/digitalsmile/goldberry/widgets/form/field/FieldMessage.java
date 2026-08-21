package io.github.digitalsmile.goldberry.widgets.form.field;

import io.github.digitalsmile.goldberry.css.ComputedStyle;
import io.github.digitalsmile.goldberry.layout.Box;
import io.github.digitalsmile.goldberry.widget.Paints;
import io.github.digitalsmile.goldberry.widget.Styled;
import io.github.digitalsmile.goldberry.widget.Widget;
import java.util.List;
import java.util.Set;

/// The line under a [Field] saying what is wrong with it — §4's message slot.
///
/// ## It is in the tree even when there is nothing to say
///
/// An empty message draws nothing and takes no height, which is what
/// `text-value` already does for an empty field. Building it only when a field
/// is invalid would be cheaper and is wrong for the reason `check-mark` is
/// always built: a node that appears on a state change cannot transition, and
/// the first frame of a newly mounted element deliberately starts nothing.
///
/// It also keeps the field's child list the same length in both states, so
/// reconciliation matches the control by position rather than shuffling it.
///
/// @param text what to say, or `""` when the field is fine
record FieldMessage(String text) implements Widget.Leaf, Styled, Paints {

    @Override
    public String cssType() {
        return "field-message";
    }

    @Override
    public Set<String> classes() {
        return Set.of();
    }

    @Override
    public Box render(ComputedStyle style, List<Box> children, Context context) {
        if (text.isEmpty()) {
            // Not a paragraph of no characters: a measured leaf over an empty
            // string still reports a line's height, so an untroubled field would
            // stand a line taller than it needs to and a form would breathe every
            // time one of its fields went wrong.
            return Box.of().style(style);
        }
        return Box.text(context.paragraph(style, text), style.color()).style(style);
    }
}
