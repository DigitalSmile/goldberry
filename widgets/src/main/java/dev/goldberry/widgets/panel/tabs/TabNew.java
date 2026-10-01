package dev.goldberry.widgets.panel.tabs;

import java.util.List;
import java.util.Set;

import dev.goldberry.css.ComputedStyle;
import dev.goldberry.input.event.KeyEvent;
import dev.goldberry.input.event.PointerEvent;
import dev.goldberry.input.handler.Handles;
import dev.goldberry.input.key.Key;
import dev.goldberry.paint.Box;
import dev.goldberry.widget.Widget;
import dev.goldberry.widget.semantics.Role;
import dev.goldberry.widget.semantics.Semantics;
import dev.goldberry.widget.style.Paints;
import dev.goldberry.widget.style.Styled;

/// The `+` at the end of a [Tabs] — a **part**, present only when the strip was
/// given an `onNew`.
///
/// Focusable, unlike [TabClose], and the difference is what the arrow keys are
/// for: adding a tab is a destination the roving selection should be able to
/// reach, where closing one belongs to the tab it is on. So the strip's arrows
/// stop here last, and `Space` or `Enter` asks for a new tab.
///
/// The mark is a `PLUS` drawn by the painter, for [TabClose]'s reason: at ten
/// logical pixels inside a control, an icon's metrics and lookup buy nothing.
///
/// @param onNew what to ask when it is chosen
record TabNew(Runnable onNew) implements Widget.Leaf, Styled, Paints, Handles, Semantics {

    @Override
    public String cssType() {
        return "tab-new";
    }

    @Override
    public Set<String> classes() {
        return Set.of();
    }

    @Override
    public boolean isFocusable() {
        return true;
    }

    @Override
    public void onPointer(PointerEvent event) {
        if (event.kind() == PointerEvent.Kind.CLICKED) {
            ask();
            event.consume();
        }
    }

    @Override
    public void onKey(KeyEvent event) {
        if (event.kind() != KeyEvent.Kind.PRESSED
                || event.isRepeat()
                || !event.modifiers().none()) {
            return;
        }
        if (event.key() == Key.SPACE || event.key() == Key.ENTER) {
            ask();
            event.consume();
        }
    }

    private void ask() {
        onNew.run();
    }

    @Override
    public Box render(ComputedStyle style, List<Box> children, Context context) {
        return Box.of().style(style).mark(new Box.Mark(Box.Mark.Kind.PLUS, style.color(), 1.5));
    }

    @Override
    public Role role() {
        return Role.BUTTON;
    }

    @Override
    public String accessibleName() {
        return "New tab";
    }
}
