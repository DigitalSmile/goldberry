package io.github.digitalsmile.goldberry.markdown.view;

import java.util.List;
import java.util.Set;

import org.jspecify.annotations.Nullable;

import io.github.digitalsmile.goldberry.css.ComputedStyle;
import io.github.digitalsmile.goldberry.input.event.KeyEvent;
import io.github.digitalsmile.goldberry.input.event.PointerEvent;
import io.github.digitalsmile.goldberry.input.handler.Handles;
import io.github.digitalsmile.goldberry.input.key.Key;
import io.github.digitalsmile.goldberry.paint.Box;
import io.github.digitalsmile.goldberry.widget.Widget;
import io.github.digitalsmile.goldberry.widget.semantics.Role;
import io.github.digitalsmile.goldberry.widget.semantics.Semantics;
import io.github.digitalsmile.goldberry.widget.style.Paints;
import io.github.digitalsmile.goldberry.widget.style.Styled;

/// A task item's check box — `task-mark`, a **part**.
///
/// Drawn rather than typed. U+2610 BALLOT BOX is in neither bundled face, so a
/// document that spelled its check boxes as characters would render them as the
/// missing-glyph box — which looks exactly like the thing it failed to draw, and is
/// the worst of both. [Box.Mark] is the toolkit's own tick, and it is the same one
/// `checkbox` draws.
///
/// A part, so it is a CSS type selector and not a widget an application builds
/// (ADR-0065).
///
/// ## It ticks now, when the application says what that means
///
/// This used to say a check box in a rendered document "is **not** interactive",
/// because ticking one means editing the Markdown behind it and the model carries no
/// source offset to edit at. ADR-0300 answers it with an **ordinal** instead: the
/// view hands over the index of the task in document order, and
/// [io.github.digitalsmile.goldberry.markdown.Markdown#toggleTask] flips the nth
/// marker in the source. The application still owns the text — nothing here writes to
/// anything — and the new document arrives by the binding that was already there.
///
/// A view with no handler builds these with none, and then it is what it was: a
/// drawn box that reports the author's state and takes no input, no Tab stop
/// included. A control that could be focused and did nothing would be worse than one
/// that cannot.
///
/// @param done whether the author ticked it
/// @param onToggle what pressing it does, or null for a document nobody has wired
record TaskMark(boolean done, @Nullable Runnable onToggle) implements Widget.Leaf, Styled, Paints, Handles, Semantics {

    /// The tick's stroke, in logical pixels. Narrower than `checkbox`'s, because the
    /// box is 14px rather than 16 and a tick as thick as a control's reads as a
    /// blotch at this size.
    private static final double THICKNESS = 1.5;

    /// An inert box, which is what a view with no task handler draws.
    TaskMark(boolean done) {
        this(done, null);
    }

    @Override
    public String cssType() {
        return "task-mark";
    }

    @Override
    public Set<String> classes() {
        // The state as a class, so the stylesheet decides what a ticked box looks
        // like -- `task-mark.done` -- rather than this record deciding in Java. And
        // `interactive`, so a document whose boxes can be pressed can show it: a
        // pointer cursor and a hover are rules rather than code.
        if (onToggle == null) {
            return done ? Set.of("done") : Set.of();
        }
        return done ? Set.of("done", "interactive") : Set.of("interactive");
    }

    /// Focusable exactly when it does something, for `canvas`'s reason (ADR-0281): a
    /// Tab stop that responds to nothing is a keyboard trap with extra steps.
    @Override
    public boolean isFocusable() {
        return onToggle != null;
    }

    @Override
    public void onPointer(PointerEvent event) {
        if (event.kind() == PointerEvent.Kind.CLICKED) {
            toggle();
            event.consume();
        }
    }

    /// `Space` and `Enter`, which is what `checkbox` takes and what a reader will
    /// try.
    @Override
    public void onKey(KeyEvent event) {
        if (event.kind() != KeyEvent.Kind.PRESSED
                || event.isRepeat()
                || !event.modifiers().none()) {
            return;
        }
        if (event.key() == Key.SPACE || event.key() == Key.ENTER) {
            toggle();
            event.consume();
        }
    }

    private void toggle() {
        if (onToggle != null) {
            onToggle.run();
        }
    }

    /// A check box either way, because that is what it **is**: a box showing a state
    /// a reader can hear. What differs is whether it can be changed, and that is
    /// [#isDisabled()] — the same distinction `checkbox` makes, rather than a
    /// document's box being a different kind of thing from a form's.
    @Override
    public Role role() {
        return Role.CHECKBOX;
    }

    /// Disabled when nothing is wired, which is what "drawn, not pressable" means to
    /// everything that asks: the router, the focus order and a screen reader.
    @Override
    public boolean isDisabled() {
        return onToggle == null;
    }

    @Override
    public Box render(ComputedStyle style, List<Box> children, Context context) {
        var box = Box.of().style(style);
        // Nothing at all when it is not ticked: an empty box is what an unticked one
        // looks like, and `checkbox`'s reason for drawing a transparent tick -- so
        // that it can be faded in -- does not apply to a box this small.
        return done ? box.mark(new Box.Mark(Box.Mark.Kind.CHECK, style.color(), THICKNESS)) : box;
    }
}
