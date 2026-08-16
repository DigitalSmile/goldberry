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
    private final Element target;
    private boolean consumed;

    public PointerEvent(Kind kind, float x, float y, Button button, int clickCount, Element target) {
        this(kind, x, y, button, clickCount, 0, 0, target);
    }

    /// A wheel event, which is the only kind that carries a delta.
    public static PointerEvent wheel(float x, float y, float deltaX, float deltaY, Element target) {
        return new PointerEvent(Kind.WHEEL, x, y, null, 0, deltaX, deltaY, target);
    }

    private PointerEvent(Kind kind, float x, float y, Button button, int clickCount,
            float deltaX, float deltaY, Element target) {
        this.kind = Objects.requireNonNull(kind, "kind");
        this.x = x;
        this.y = y;
        this.button = button;
        this.clickCount = clickCount;
        this.deltaX = deltaX;
        this.deltaY = deltaY;
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
