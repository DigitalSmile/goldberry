package io.github.digitalsmile.goldberry.widgets.panel.collapse;

import io.github.digitalsmile.goldberry.css.ComputedStyle;
import io.github.digitalsmile.goldberry.input.Handles;
import io.github.digitalsmile.goldberry.input.KeyEvent;
import io.github.digitalsmile.goldberry.input.PointerEvent;
import io.github.digitalsmile.goldberry.layout.Box;
import io.github.digitalsmile.goldberry.widget.Paints;
import io.github.digitalsmile.goldberry.widget.Styled;
import io.github.digitalsmile.goldberry.widget.Widget;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/// The row you click to open a [Collapse] — §5's "disclosure button".
///
/// **One tab stop, and the only focusable thing in a `collapse`.** §5: "The
/// header is one Tab stop, `Enter`/`Space` toggles, `Left`/`Right` close and
/// open."
///
/// `Left` and `Right` are absolute rather than a toggle, which is what the
/// specification asks for and what a disclosure does everywhere: pressing `Right`
/// on an open section leaves it open. A user holding `Right` down a list of
/// sections opens all of them, where a toggle would flap the one under the
/// cursor.
record CollapseHeader(String title, boolean open, Runnable onToggle)
        implements Widget.Leaf, Styled, Paints, Handles {

    @Override
    public String cssType() {
        return "collapse-header";
    }

    @Override
    public Set<String> classes() {
        return open ? Set.of("open") : Set.of();
    }

    @Override
    public boolean isFocusable() {
        return true;
    }

    /// Mirrored to `:checked`, so "this section is showing" is a state a
    /// stylesheet can select rather than a second drawing — the same use
    /// `checkbox` and `item` make of it.
    @Override
    public boolean isChecked() {
        return open;
    }

    @Override
    public void onPointer(PointerEvent event) {
        if (event.kind() == PointerEvent.Kind.CLICKED) {
            toggle();
            event.consume();
        }
    }

    @Override
    public void onKey(KeyEvent event) {
        if (event.kind() != KeyEvent.Kind.PRESSED || event.isRepeat()
                || !event.modifiers().none()) {
            return;
        }
        switch (event.key()) {
            case ENTER, SPACE -> {
                toggle();
                event.consume();
            }
            // Absolute, not a toggle: see the class note.
            case RIGHT -> {
                if (!open) {
                    toggle();
                }
                event.consume();
            }
            case LEFT -> {
                if (open) {
                    toggle();
                }
                event.consume();
            }
            default -> {
            }
        }
    }

    private void toggle() {
        if (onToggle != null) {
            onToggle.run();
        }
    }

    /// The marker, as a child rather than a mark drawn here.
    ///
    /// It has to be a node the cascade reaches: §5 asks for a chevron that
    /// **rotates on `base`**, a rotation is a `transform`, and a transform is
    /// resolved for an element — so a mark drawn inline by this widget could
    /// never turn. That is also why it is `CHEVRON_END` turned by the stylesheet
    /// rather than `CHEVRON_DOWN` swapped in when the section opens: a mark that
    /// changed *kind* would jump where §5 wants it to travel, and `transform` is
    /// on §1.7's whitelist precisely so that travelling costs no layout.
    @Override
    public List<Widget> children() {
        return List.of(new CollapseChevron(open));
    }

    /// The chevron leads, then the title.
    ///
    /// **Leading**, unlike a menu row's trailing one, because a column of
    /// sections is read down its left edge and a disclosure marker at the far
    /// right of a wide panel is nowhere near the word it belongs to.
    @Override
    public Box render(ComputedStyle style, List<Box> children, Context context) {
        var content = new ArrayList<Box>(2);
        content.addAll(children);
        content.add(Box.text(context.paragraph(style, title), style.color()).shrink(0));
        return Box.of().style(style).children(content.toArray(Box[]::new));
    }

    /// The disclosure marker — a **part**, so it is CSS-selectable and not
    /// constructible
    /// ([ADR-0065](../../../../../../../../book/src/adr/0065-a-part-is-styleable-and-not-constructible.md)).
    record CollapseChevron(boolean open) implements Widget.Leaf, Styled, Paints {

        @Override
        public String cssType() {
            return "collapse-chevron";
        }

        @Override
        public Set<String> classes() {
            return open ? Set.of("open") : Set.of();
        }

        @Override
        public Box render(ComputedStyle style, List<Box> children, Context context) {
            return Box.of().style(style)
                    .mark(new Box.Mark(Box.Mark.Kind.CHEVRON_END, style.color(), 1.5));
        }
    }
}
