package dev.goldberry.widgets.controls.select;

import java.util.List;
import java.util.Set;

import dev.goldberry.css.ComputedStyle;
import dev.goldberry.input.event.PointerEvent;
import dev.goldberry.input.handler.Handles;
import dev.goldberry.paint.Box;
import dev.goldberry.widget.Widget;
import dev.goldberry.widget.semantics.Role;
import dev.goldberry.widget.semantics.Semantics;
import dev.goldberry.widget.style.Paints;
import dev.goldberry.widget.style.Styled;

/// One of a `select multiple`'s chosen values, shown as a chip inside the
/// closed control with a × that takes it out again.
///
/// A part, styleable as `select-chip` and not constructible from outside the
/// package: nobody writes a `select-chip`, a `select multiple` describes one
/// per value it was handed.
///
/// ## It is a `badge` and it is not the `badge` widget
///
/// `controls.css` gives this the badge's metrics by naming both types in one
/// rule rather than by copying them. It could not simply *be* a
/// [dev.goldberry.widgets.controls.badge.Badge]: that is a leaf with text and no
/// children, and a chip has to hold a remove affordance beside its label.
///
/// Read more: [Choices](https://goldberry.dev/docs/components/choices.html#select).
///
/// @param label    what it reads — the option's label, not its value, for the
///                 reason `option value="nord-dark" "Nord Dark"` exists
/// @param onRemove what to ask when the × is clicked
record SelectChip(String label, Runnable onRemove) implements Widget.Leaf, Styled, Paints, Semantics {

    @Override
    public String cssType() {
        return "select-chip";
    }

    @Override
    public Set<String> classes() {
        return Set.of();
    }

    @Override
    public List<Widget> children() {
        return List.of(new SelectChipLabel(label), new SelectChipRemove(onRemove));
    }

    @Override
    public Box render(ComputedStyle style, List<Box> boxes, Context context) {
        return Box.of().style(style).children(boxes.toArray(Box[]::new));
    }

    /// The chip's words. A child box rather than text on the chip's own node,
    /// because a box with text is a measured leaf and Yoga never lays a measured
    /// node's children out — which would leave nowhere for the ×.
    record SelectChipLabel(String text) implements Widget.Leaf, Styled, Paints {

        @Override
        public String cssType() {
            return "select-chip-label";
        }

        @Override
        public Set<String> classes() {
            return Set.of();
        }

        @Override
        public Box render(ComputedStyle style, List<Box> children, Context context) {
            return Box.of().style(style).children(Box.text(context.paragraph(style, text), style.color()));
        }
    }

    /// The × that takes one value back out —
    /// [dev.goldberry.widgets.panel.tabs.TabClose]'s
    /// shape, and not focusable for the same reason: a `select` is **one** Tab
    /// stop, and a focusable × per chip would make a five-value select six stops
    /// where a document wrote one control.
    ///
    /// The keyboard's way to remove a value is to open the list and press `Enter`
    /// on it, which toggles — so nothing is unreachable without a pointer.
    record SelectChipRemove(Runnable onRemove) implements Widget.Leaf, Styled, Paints, Handles {

        @Override
        public String cssType() {
            return "select-chip-remove";
        }

        @Override
        public Set<String> classes() {
            return Set.of();
        }

        @Override
        public boolean isFocusable() {
            return false;
        }

        /// **Consumed**, so removing a value does not also open the list under
        /// it. Taking a chip off the field is one gesture, not two — which is
        /// `TabClose`'s rule and the same mistake it was written to avoid.
        @Override
        public void onPointer(PointerEvent event) {
            if (event.kind() == PointerEvent.Kind.CLICKED) {
                onRemove.run();
                event.consume();
            }
        }

        @Override
        public Box render(ComputedStyle style, List<Box> children, Context context) {
            return Box.of().style(style).mark(new Box.Mark(Box.Mark.Kind.CROSS, style.color(), 1.5));
        }
    }

    @Override
    public Role role() {
        return Role.OPTION;
    }

    @Override
    public String accessibleName() {
        return label;
    }
}
