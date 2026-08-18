package io.github.digitalsmile.goldberry.widgets.panel.tabs;

import io.github.digitalsmile.goldberry.css.ComputedStyle;
import io.github.digitalsmile.goldberry.input.Handles;
import io.github.digitalsmile.goldberry.input.Key;
import io.github.digitalsmile.goldberry.input.KeyEvent;
import io.github.digitalsmile.goldberry.input.PointerEvent;
import io.github.digitalsmile.goldberry.layout.Box;
import io.github.digitalsmile.goldberry.widget.Paints;
import io.github.digitalsmile.goldberry.widget.Styled;
import io.github.digitalsmile.goldberry.widget.Widget;
import java.util.List;
import java.util.Set;

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
record TabNew(Runnable onNew) implements Widget.Leaf, Styled, Paints, Handles {

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
        if (event.kind() != KeyEvent.Kind.PRESSED || event.isRepeat()
                || !event.modifiers().none()) {
            return;
        }
        if (event.key() == Key.SPACE || event.key() == Key.ENTER) {
            ask();
            event.consume();
        }
    }

    private void ask() {
        if (onNew != null) {
            onNew.run();
        }
    }

    @Override
    public Box render(ComputedStyle style, List<Box> children, Context context) {
        return Box.of().style(style)
                .mark(new Box.Mark(Box.Mark.Kind.PLUS, style.color(), 1.5));
    }
}
