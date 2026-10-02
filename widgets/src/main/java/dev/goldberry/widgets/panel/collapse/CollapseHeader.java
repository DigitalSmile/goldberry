package dev.goldberry.widgets.panel.collapse;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import dev.goldberry.css.ComputedStyle;
import dev.goldberry.input.event.KeyEvent;
import dev.goldberry.input.event.PointerEvent;
import dev.goldberry.input.handler.Handles;
import dev.goldberry.paint.Box;
import dev.goldberry.widget.Widget;
import dev.goldberry.widget.semantics.Role;
import dev.goldberry.widget.semantics.Semantics;
import dev.goldberry.widget.style.Paints;
import dev.goldberry.widget.style.Styled;

/// The row you click to open a [Collapse]: its disclosure button.
///
/// **One tab stop, and the only focusable thing in a `collapse`.** `Enter` and
/// `Space` toggle it; `Left` closes and `Right` opens.
///
/// `Left` and `Right` are absolute rather than a toggle, which is what a
/// disclosure does everywhere: pressing `Right`
/// on an open section leaves it open. A user holding `Right` down a list of
/// sections opens all of them, where a toggle would flap the one under the
/// cursor.
record CollapseHeader(String title, boolean open, Runnable onToggle)
        implements Widget.Leaf, Styled, Paints, Handles, Semantics {

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
        if (event.kind() != KeyEvent.Kind.PRESSED
                || event.isRepeat()
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
            default -> {}
        }
    }

    private void toggle() {
        onToggle.run();
    }

    /// The marker, as a child rather than a mark drawn here.
    ///
    /// It has to be a node the cascade reaches: the chevron **rotates on
    /// `base`**, a rotation is a `transform`, and a transform is
    /// resolved for an element — so a mark drawn inline by this widget could
    /// never turn. That is also why it is `CHEVRON_END` turned by the stylesheet
    /// rather than `CHEVRON_DOWN` swapped in when the section opens: a mark that
    /// changed *kind* would jump where this one travels, and `transform` is one
    /// of the few properties allowed to animate precisely so that travelling
    /// costs no layout.
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

    /// The disclosure marker — a part, so a stylesheet can select it and a
    /// document cannot write it.
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
            return Box.of().style(style).mark(new Box.Mark(Box.Mark.Kind.CHEVRON_END, style.color(), 1.5));
        }
    }

    @Override
    public Role role() {
        return Role.DISCLOSURE;
    }

    @Override
    public String accessibleName() {
        return title;
    }
}
