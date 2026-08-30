package io.github.digitalsmile.goldberry.widgets.controls.select;

import java.util.List;
import java.util.Set;

import io.github.digitalsmile.goldberry.css.ComputedStyle;
import io.github.digitalsmile.goldberry.input.event.PointerEvent;
import io.github.digitalsmile.goldberry.input.handler.Handles;
import io.github.digitalsmile.goldberry.paint.Box;
import io.github.digitalsmile.goldberry.widget.Widget;
import io.github.digitalsmile.goldberry.widget.semantics.Role;
import io.github.digitalsmile.goldberry.widget.semantics.Semantics;
import io.github.digitalsmile.goldberry.widget.style.Paints;
import io.github.digitalsmile.goldberry.widget.style.Styled;

/// One of a `select multiple`'s chosen values, shown in the closed control —
/// §3's "`badge` chips inside the closed control, each with a remove
/// affordance".
///
/// A **part**, so it is styleable and not constructible
/// (ADR-0065):
/// nobody writes a `select-chip`, a `select multiple` describes one per value it
/// was handed.
///
/// ## It is a `badge` and it is not the `badge` widget
///
/// §3 says "badge chips", and `controls.css` gives this the badge's metrics by
/// naming both types in one rule rather than by copying them. It could not
/// simply *be* a [io.github.digitalsmile.goldberry.widgets.controls.badge.Badge]:
/// that is a leaf with text and no children, and a chip has to hold a remove
/// affordance beside its label ([ADR-0182]).
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
    /// [io.github.digitalsmile.goldberry.widgets.panel.tabs.TabClose]'s
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
                if (onRemove != null) {
                    onRemove.run();
                }
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
