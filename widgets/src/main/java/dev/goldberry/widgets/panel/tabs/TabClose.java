package dev.goldberry.widgets.panel.tabs;

import java.util.List;
import java.util.Set;

import org.jspecify.annotations.Nullable;

import dev.goldberry.css.ComputedStyle;
import dev.goldberry.input.event.PointerEvent;
import dev.goldberry.input.handler.Handles;
import dev.goldberry.paint.Box;
import dev.goldberry.widget.Widget;
import dev.goldberry.widget.semantics.Role;
import dev.goldberry.widget.semantics.Semantics;
import dev.goldberry.widget.style.Paints;
import dev.goldberry.widget.style.Styled;

/// The × on a closable [Tab] — a part, so a stylesheet can select it and a
/// document cannot write it.
///
/// **Not focusable**, which is the decision worth writing down: a tab strip is
/// *one* Tab stop with the arrows roving inside it, and a focusable close
/// affordance would make it two per tab — nine tabs would be nineteen stops
/// between the strip and the content. The keyboard's way to close a tab is
/// `Delete` on the tab itself, which [Tab] handles.
///
/// The mark is a `CROSS`, drawn by the painter rather than as an icon, so a close
/// affordance costs no icon lookup and scales with the tab's own colour.
///
/// @param onClose what to ask when it is clicked
record TabClose(@Nullable Runnable onClose) implements Widget.Leaf, Styled, Paints, Handles, Semantics {

    @Override
    public String cssType() {
        return "tab-close";
    }

    @Override
    public Set<String> classes() {
        return Set.of();
    }

    /// See the class note: the strip is one Tab stop, and this is inside it.
    @Override
    public boolean isFocusable() {
        return false;
    }

    /// Consumed, so the click does **not** also select the tab it is on. Closing
    /// the tab you are looking at is one gesture, not two.
    @Override
    public void onPointer(PointerEvent event) {
        if (event.kind() == PointerEvent.Kind.CLICKED) {
            if (onClose != null) {
                onClose.run();
            }
            event.consume();
        }
    }

    @Override
    public Box render(ComputedStyle style, List<Box> children, Context context) {
        return Box.of().style(style).mark(new Box.Mark(Box.Mark.Kind.CROSS, style.color(), 1.5));
    }

    @Override
    public Role role() {
        return Role.BUTTON;
    }

    @Override
    public String accessibleName() {
        return "Close";
    }
}
