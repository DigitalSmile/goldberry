package io.github.digitalsmile.goldberry.widgets.core.canvas;

import java.util.Optional;

import org.jspecify.annotations.Nullable;

import io.github.digitalsmile.goldberry.input.event.KeyEvent;
import io.github.digitalsmile.goldberry.input.event.PointerEvent;
import io.github.digitalsmile.goldberry.input.event.PreeditEvent;
import io.github.digitalsmile.goldberry.input.event.TextEvent;
import io.github.digitalsmile.goldberry.render.model.LogicalRect;

/// What a `canvas` does with input — [io.github.digitalsmile.goldberry.paint.Painter]'s
/// sibling.
///
/// ```java
/// new Canvas(painter, new Input() {
///     public void onPointer(PointerEvent event) {
///         var at = event.content();          // canvas coordinates, like the painter's
///         switch (event.kind()) {
///             case PRESSED -> board.beginDrag(at.x(), at.y());
///             case MOVED   -> board.dragTo(at.x(), at.y());
///             case WHEEL   -> board.zoom(event.deltaY(), at.x(), at.y());
///             default      -> { }
///         }
///     }
/// });
/// ```
///
/// ## The events are the toolkit's own
///
/// [PointerEvent] and [KeyEvent] are handed over as they are, rather than
/// re-expressed as a `CanvasPointerEvent`. They already carry everything a board
/// tool needs — the button, the click count, the modifier keys, the wheel's
/// fraction *and* its detents, and where the gesture started — and a second
/// vocabulary over the same facts would be one more thing to keep in step
/// (ADR-0281).
///
/// **Read [PointerEvent#content()], not `local()`.** `content()` is measured from
/// the rectangle the painter draws in, which is inside the padding; `local()` is
/// measured from the box's own corner. They differ by exactly the padding, and
/// for `canvas { padding: 8px }` — which `docs/core-widgets.md` §1 documents as a
/// framed drawing surface — that difference is eight pixels of everything landing
/// in the wrong place.
///
/// ## What comes for free
///
/// - **A drag that leaves the canvas still arrives.** The router captures the
///   pointer implicitly on press and releases it on the matching release, so a
///   marquee dragged off the edge keeps reporting (ADR-0057). Nothing has to ask.
/// - **The wheel is a [PointerEvent] of kind `WHEEL`**, with `deltaY()` for a
///   touchpad's fraction and `ticksY()` for a mouse's detents.
/// - **`consume()` stops the event**, which is how a canvas keeps a wheel from
///   scrolling the pane it sits in.
/// - **The cursor is the stylesheet's.** `canvas { cursor: crosshair }` works like
///   it does on any other box, and a tool that wants to change it mid-drag sets a
///   class.
///
/// ## Keys need focus
///
/// A canvas is focusable exactly when it has an `Input` — see
/// [Canvas#isFocusable()]. Without one there is nothing to deliver a key to, and
/// a canvas that took a Tab stop to draw a chart would be a keyboard trap with no
/// exit.
public interface Input {

    /// A press, a release, a move, a drag, a click, an enter, an exit or a wheel.
    ///
    /// One method for all of them because that is what [PointerEvent#kind()] is,
    /// and because a tool usually cares about the sequence rather than about one
    /// of them.
    default void onPointer(PointerEvent event) {}

    /// A key, when this canvas has the focus.
    default void onKey(KeyEvent event) {}

    /// Committed text, when this canvas has the focus.
    ///
    /// Not the same as [#onKey]: this is what an input method produced, which for
    /// anything but a Latin keyboard is not a key at all. It is what
    /// [io.github.digitalsmile.goldberry.text.edit.Editor#onText] takes, which is
    /// how a canvas gets a caret in it (ADR-0285).
    default void onText(TextEvent event) {}

    /// The composition an input method is assembling, before the user has
    /// accepted it — `docs/gaps.md` G15.
    ///
    /// [io.github.digitalsmile.goldberry.text.edit.Editor#onPreedit] takes it
    /// whole. A canvas that ignores it still receives every committed character,
    /// which is what a Latin keyboard produces; what a Japanese, Chinese or
    /// Korean user loses is the underlined string they watch while choosing, and
    /// without it they type blind until they commit (ADR-0289).
    ///
    /// **Not an edit.** See
    /// [io.github.digitalsmile.goldberry.input.event.PreeditEvent].
    default void onPreedit(PreeditEvent event) {}

    /// Where this canvas's caret is, in the **same coordinates the painter draws
    /// in** — what [io.github.digitalsmile.goldberry.input.event.PointerEvent#content()]
    /// is measured in.
    ///
    /// Handed to the platform so an input method can put its candidate window
    /// beside the text (`docs/gaps.md` G15). Only a canvas knows where its own
    /// caret is, which is why this is a question rather than something the
    /// toolkit works out.
    ///
    /// ```java
    /// public Optional<LogicalRect> caretArea() {
    ///     return Optional.of(editor.caretLine().offsetBy(8, 8));   // the painter's own origin
    /// }
    /// ```
    ///
    /// Empty — the default — means "nothing is being typed into me", which is
    /// what a chart answers.
    default Optional<LogicalRect> caretArea() {
        return Optional.empty();
    }

    /// Where the caret is inside [#caretArea], as an x offset from its left edge
    /// — [io.github.digitalsmile.goldberry.text.edit.Editor#caret]`.x()`.
    default double caretOffsetIn(LogicalRect area) {
        return 0;
    }

    /// The focus arrived or left.
    ///
    /// **What a caret is for.** Everything else a canvas draws looks the same
    /// focused or not, so this was not worth a method until something on a canvas
    /// had to stop blinking when the user clicked elsewhere (ADR-0285). A canvas
    /// that ignores it draws the same picture either way, which is what a chart
    /// wants.
    ///
    /// @param fromKeyboard whether the focus arrived by `Tab` rather than by a
    ///        click — the same distinction `:focus-visible` is drawn on
    default void onFocusChanged(boolean focused, boolean fromKeyboard) {}

    /// What a screen reader should call this canvas, or null for nothing.
    ///
    /// A canvas is a [io.github.digitalsmile.goldberry.widget.semantics.Role#FIGURE]
    /// — "a picture of data, which a reader reaches with the keyboard" — and what
    /// that picture *is* is the application's to say. A board, a waveform, a
    /// seating plan: the toolkit knows only that something was drawn.
    ///
    /// Null is an answer where the canvas is named by something beside it, which
    /// is the same rule every other widget's name follows.
    default @Nullable String accessibleName() {
        return null;
    }

    /// Whether the platform's text input — the input method, the dead keys, the
    /// on-screen keyboard where there is one — should be on while this canvas has
    /// the focus.
    ///
    /// **False by default, and this is the switch a canvas being typed into
    /// needs.** Committed text is delivered to whatever has the focus, but on a
    /// desktop the platform does not *produce* any until it is told that
    /// something is being typed into: a canvas holding an
    /// [io.github.digitalsmile.goldberry.text.edit.Editor] and not saying so gets
    /// keys and never a character (ADR-0285). A board that only wants arrow keys
    /// leaves it false, because turning it on pops a keyboard over the board on
    /// the platforms that have one.
    default boolean wantsText() {
        return false;
    }

    /// Whether this canvas takes the focus, and so whether keys reach it.
    ///
    /// True by default, because an `Input` that never wanted a key would more
    /// simply not have been given. A canvas with no `Input` is not focusable
    /// whatever this says.
    default boolean focusable() {
        return true;
    }
}
