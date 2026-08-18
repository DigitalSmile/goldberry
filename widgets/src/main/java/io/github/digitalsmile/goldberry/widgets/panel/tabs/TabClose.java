package io.github.digitalsmile.goldberry.widgets.panel.tabs;

import io.github.digitalsmile.goldberry.css.ComputedStyle;
import io.github.digitalsmile.goldberry.input.Handles;
import io.github.digitalsmile.goldberry.input.PointerEvent;
import io.github.digitalsmile.goldberry.layout.Box;
import io.github.digitalsmile.goldberry.widget.Paints;
import io.github.digitalsmile.goldberry.widget.Styled;
import io.github.digitalsmile.goldberry.widget.Widget;
import java.util.List;
import java.util.Set;

/// The × on a closable [Tab] — a **part**, so it is styleable and not
/// constructible ([ADR-0065](../../../../../../../../book/src/adr/0065-a-part-is-styleable-and-not-constructible.md)).
///
/// **Not focusable**, which is the decision worth writing down: a tab strip is
/// *one* Tab stop with the arrows roving inside it (§7.2), and a focusable close
/// affordance would make it two per tab — nine tabs would be nineteen stops
/// between the strip and the content. The keyboard's way to close a tab is
/// `Delete` on the tab itself, which [Tab] handles.
///
/// The mark is a `CROSS`, drawn by the painter rather than as an icon, so a close
/// affordance costs no icon lookup and scales with the tab's own colour.
///
/// @param onClose what to ask when it is clicked
record TabClose(Runnable onClose) implements Widget.Leaf, Styled, Paints, Handles {

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
        return Box.of().style(style)
                .mark(new Box.Mark(Box.Mark.Kind.CROSS, style.color(), 1.5));
    }
}
