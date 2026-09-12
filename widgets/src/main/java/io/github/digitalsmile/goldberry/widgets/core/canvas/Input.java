package io.github.digitalsmile.goldberry.widgets.core.canvas;

import org.jspecify.annotations.Nullable;

import io.github.digitalsmile.goldberry.input.event.KeyEvent;
import io.github.digitalsmile.goldberry.input.event.PointerEvent;
import io.github.digitalsmile.goldberry.input.event.TextEvent;

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
    /// anything but a Latin keyboard is not a key at all. In-canvas *editing* —
    /// a caret, a selection, preedit — is `docs/gaps.md` G6 and is not this.
    default void onText(TextEvent event) {}

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

    /// Whether this canvas takes the focus, and so whether keys reach it.
    ///
    /// True by default, because an `Input` that never wanted a key would more
    /// simply not have been given. A canvas with no `Input` is not focusable
    /// whatever this says.
    default boolean focusable() {
        return true;
    }
}
