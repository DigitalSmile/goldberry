package io.github.digitalsmile.goldberry.input;

import io.github.digitalsmile.goldberry.widget.Element;
import java.util.Objects;

/// A pointer event as a widget sees it.
///
/// Positions are **logical**, like everything an application touches: the window
/// scale is applied when the frame is rasterized (ADR-0031), not here, so a
/// coordinate means the same thing on a 150% display as on a 100% one.
public final class PointerEvent {

    /// What happened.
    public enum Kind {
        /// The pointer moved, with no button state change.
        MOVED,
        /// A button went down.
        PRESSED,
        /// A button came up.
        RELEASED,
        /// The pointer entered this node's bounds. Synthetic, derived from
        /// pointer flow rather than sent by the platform (§7.1).
        ENTERED,
        /// The pointer left. Synthetic.
        EXITED,
        /// A press and its release both landed on this node. Synthetic, and the
        /// one every control actually wants.
        ///
        /// Not the same as a release: a press that is dragged away and let go
        /// elsewhere still releases — the captor is told, so it can stop looking
        /// pressed — but it was not a click, and the user cancelling a click by
        /// dragging off is a gesture people rely on (§7.1).
        CLICKED,
        /// The wheel turned, or a touchpad scrolled — see [#deltaX] and
        /// [#deltaY].
        WHEEL
    }

    /// Which button, for [Kind#PRESSED] and [Kind#RELEASED].
    public enum Button {
        PRIMARY, SECONDARY, MIDDLE
    }

    private final Kind kind;
    private final float x;
    private final float y;
    private final Button button;
    private final int clickCount;
    private final float deltaX;
    private final float deltaY;
    private final float pressX;
    private final float pressY;
    private final Element target;
    private boolean consumed;
    private Local local = Local.UNKNOWN;

    /// Where an event happened **inside the widget currently handling it**, and
    /// how big that widget is.
    ///
    /// The other half of [#dragX()], and the router owns it for the same reason:
    /// a widget is a value with no idea where it was laid out, and the router is
    /// holding the hit-test snapshot that says. A slider asking "how far along me
    /// is the pointer" cannot answer it any other way.
    ///
    /// **Relative to the handler, not to [#target()].** Dispatch bubbles, so one
    /// event is delivered to a chain of widgets and each needs its own answer: a
    /// press on a slider's thumb targets the thumb, and the slider handling that
    /// press wants the position along *itself*. The router re-points this before
    /// each handler runs.
    ///
    /// @param x      distance from the handler's left edge, in logical pixels
    /// @param y      distance from its top edge
    /// @param width  the handler's own width
    /// @param height its height
    public record Local(float x, float y, float width, float height) {

        /// What a widget that was never laid out gets — a widget built and
        /// poked directly by a test, or one whose box is not in the snapshot
        /// yet. Zero-sized, so [#fractionX()] is 0 rather than a division by
        /// zero.
        public static final Local UNKNOWN = new Local(0, 0, 0, 0);

        /// [#x()] as a fraction of [#width()], clamped to `0..1`.
        ///
        /// What a horizontal slider reads off a press: the value the user is
        /// pointing at, before any step is applied.
        public double fractionX() {
            return width <= 0 ? 0 : clamp(x / width);
        }

        /// [#y()] as a fraction of [#height()], clamped to `0..1` — a vertical
        /// slider's, and **not** inverted. Zero is the top, because that is where
        /// zero is on a screen; a widget whose maximum is at the top inverts it,
        /// which is a fact about the widget rather than about the pointer.
        public double fractionY() {
            return height <= 0 ? 0 : clamp(y / height);
        }

        private static double clamp(double value) {
            return value < 0 ? 0 : value > 1 ? 1 : value;
        }
    }

    /// See [Local].
    public Local local() {
        return local;
    }

    /// Re-points [#local()] at the widget about to handle this.
    ///
    /// **The router's, in an application.** It is the only thing holding the
    /// hit-test snapshot, and it re-points this before each handler on the chain.
    /// Public because a test that builds an event by hand has to say where it
    /// landed, exactly as it has to be able to call [#consume()].
    public void localTo(Local value) {
        this.local = value == null ? Local.UNKNOWN : value;
    }

    public PointerEvent(Kind kind, float x, float y, Button button, int clickCount, Element target) {
        this(kind, x, y, button, clickCount, 0, 0, Float.NaN, Float.NaN, target);
    }

    /// An event delivered while a button is held, carrying where it went down.
    ///
    /// The router is the only caller, because it is the only thing that knows —
    /// see [#dragX()].
    public PointerEvent(Kind kind, float x, float y, Button button, int clickCount,
            float pressX, float pressY, Element target) {
        this(kind, x, y, button, clickCount, 0, 0, pressX, pressY, target);
    }

    /// A wheel event, which is the only kind that carries a delta.
    public static PointerEvent wheel(float x, float y, float deltaX, float deltaY, Element target) {
        return new PointerEvent(Kind.WHEEL, x, y, null, 0, deltaX, deltaY, Float.NaN, Float.NaN, target);
    }

    private PointerEvent(Kind kind, float x, float y, Button button, int clickCount,
            float deltaX, float deltaY, float pressX, float pressY, Element target) {
        this.kind = Objects.requireNonNull(kind, "kind");
        this.x = x;
        this.y = y;
        this.button = button;
        this.clickCount = clickCount;
        this.deltaX = deltaX;
        this.deltaY = deltaY;
        this.pressX = pressX;
        this.pressY = pressY;
        this.target = target;
    }

    public Kind kind() {
        return kind;
    }

    /// Logical x, in the window's coordinates.
    public float x() {
        return x;
    }

    /// Logical y.
    public float y() {
        return y;
    }

    /// The button, or null for a move, enter or exit.
    public Button button() {
        return button;
    }

    /// 1 for a single click, 2 for a double, and so on.
    public int clickCount() {
        return clickCount;
    }

    /// How far this scrolled horizontally, in **lines**. Zero for every other
    /// kind.
    ///
    /// Positive is to the right, which is the direction the content should move
    /// *under* the viewport — the CSS convention, and the opposite of SDL's.
    public float deltaX() {
        return deltaX;
    }

    /// How far this scrolled vertically, in lines. Positive is **down**.
    ///
    /// Lines rather than pixels, because that is the only unit SDL reports:
    /// there is no pixel-precise delta on the other side of this API to pass on.
    /// It is a `float` and it is routinely fractional — a touchpad reports
    /// fractions of a detent, and a scroll view that rounds them scrolls in
    /// jerks. Multiply by whatever a line is worth in the thing being scrolled.
    public float deltaY() {
        return deltaY;
    }

    /// Where the button went down, or `NaN` if none is held.
    public float pressX() {
        return pressX;
    }

    /// The other half of [#pressX()].
    public float pressY() {
        return pressY;
    }

    /// How far the pointer has travelled since the button went down, positive to
    /// the right — **`NaN` when no button is held**.
    ///
    /// A gesture is a sequence of events and a widget is a value rebuilt every
    /// frame, so a widget cannot remember where a drag started. The router can:
    /// it already takes an implicit capture on the press
    /// ([ADR-0058](../../../../../../book/src/adr/0058-a-press-captures-the-pointer.md)),
    /// which is the same span this is defined over, and it is the only thing in
    /// the toolkit that sees both ends. The argument is the one already written
    /// on Tab and on arrow keys: the router owns what the widget cannot see.
    ///
    /// `NaN` rather than zero for "no press", because zero is a real answer —
    /// it is what a press with no movement gives — and a widget comparing
    /// `Math.abs(dragX()) >= threshold` against `NaN` gets `false`, which is
    /// "this was not a drag". The wrong reading of the wrong value is the right
    /// behaviour, which is not true of zero.
    public float dragX() {
        return x - pressX;
    }

    /// How far the pointer has travelled since the button went down, positive
    /// **down**. `NaN` when no button is held — see [#dragX()].
    public float dragY() {
        return y - pressY;
    }

    /// The node the event was aimed at — the deepest one under the pointer.
    ///
    /// Stays the same through capture and bubble, so a handler on an ancestor
    /// can tell "this happened to me" from "this happened below me".
    public Element target() {
        return target;
    }

    /// Stops this event travelling any further.
    ///
    /// §7.1: `consume()` stops propagation. It does not undo the phases already
    /// delivered — an ancestor that consumed during capture has already stopped
    /// the target from ever seeing it, which is the point of capture.
    public void consume() {
        consumed = true;
    }

    public boolean isConsumed() {
        return consumed;
    }

    @Override
    public String toString() {
        return kind + " at " + x + "," + y + (button == null ? "" : " " + button)
                + (kind == Kind.WHEEL ? " by " + deltaX + "," + deltaY : "")
                + (consumed ? " (consumed)" : "");
    }
}
