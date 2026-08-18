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
    private final Modifiers modifiers;
    private double anchor = Double.NaN;
    private Modifiers gestureModifiers = Modifiers.NONE;
    private final float x;
    private final float y;
    private final Button button;
    private final int clickCount;
    private final float deltaX;
    private final float deltaY;
    private final int ticksX;
    private final int ticksY;
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
        this(kind, x, y, button, clickCount, 0, 0, Float.NaN, Float.NaN, Modifiers.NONE, target);
    }

    /// An event delivered while a button is held, carrying where it went down.
    ///
    /// The router is the only caller, because it is the only thing that knows —
    /// see [#dragX()].
    public PointerEvent(Kind kind, float x, float y, Button button, int clickCount,
            float pressX, float pressY, Element target) {
        this(kind, x, y, button, clickCount, 0, 0, pressX, pressY, Modifiers.NONE, target);
    }

    /// The same, with the modifier keys that were held.
    public PointerEvent(Kind kind, float x, float y, Button button, int clickCount,
            float pressX, float pressY, Modifiers modifiers, Element target) {
        this(kind, x, y, button, clickCount, 0, 0, pressX, pressY, modifiers, target);
    }

    /// A wheel event, which is the only kind that carries a delta.
    public static PointerEvent wheel(float x, float y, float deltaX, float deltaY, Element target) {
        return wheel(x, y, deltaX, deltaY, Modifiers.NONE, target);
    }

    /// A wheel event stating its detents as well as its fraction.
    ///
    /// The backend's constructor. Everything else truncates, which is right for a
    /// synthesized event and wrong for a real touchpad — see [#ticksY()].
    public static PointerEvent wheel(float x, float y, float deltaX, float deltaY,
            int ticksX, int ticksY, Modifiers modifiers, Element target) {
        return new PointerEvent(Kind.WHEEL, x, y, null, 0, deltaX, deltaY, ticksX, ticksY,
                Float.NaN, Float.NaN, modifiers, target);
    }

    /// A wheel event with modifiers — `Shift` for a fine step, `Ctrl` for zoom.
    public static PointerEvent wheel(float x, float y, float deltaX, float deltaY,
            Modifiers modifiers, Element target) {
        // Truncated rather than rounded: an accumulator has not reached one
        // click at 0.5. A caller that knows better passes the detents in.
        return new PointerEvent(Kind.WHEEL, x, y, null, 0, deltaX, deltaY,
                (int) deltaX, (int) deltaY, Float.NaN, Float.NaN, modifiers, target);
    }

    private PointerEvent(Kind kind, float x, float y, Button button, int clickCount,
            float deltaX, float deltaY, float pressX, float pressY,
            Modifiers modifiers, Element target) {
        this(kind, x, y, button, clickCount, deltaX, deltaY, (int) deltaX, (int) deltaY,
                pressX, pressY, modifiers, target);
    }

    private PointerEvent(Kind kind, float x, float y, Button button, int clickCount,
            float deltaX, float deltaY, int ticksX, int ticksY, float pressX, float pressY,
            Modifiers modifiers, Element target) {
        this.kind = Objects.requireNonNull(kind, "kind");
        this.x = x;
        this.y = y;
        this.button = button;
        this.clickCount = clickCount;
        this.deltaX = deltaX;
        this.deltaY = deltaY;
        this.ticksX = ticksX;
        this.ticksY = ticksY;
        this.pressX = pressX;
        this.pressY = pressY;
        this.modifiers = modifiers == null ? Modifiers.NONE : modifiers;
        this.target = target;
    }

    /// Which modifier keys were held when this happened.
    ///
    /// On the pointer and not only on the keyboard, because
    /// `docs/design-system.md` §3 asks a knob for a "modifier for fine
    /// adjustment" — and §2.3's `Ctrl+click` and `Shift+click` want the same
    /// thing. Read from the platform at the moment the event was translated
    /// rather than latched from the last key event: a window that loses focus
    /// while Shift is held never sees the key release, and a latched flag would
    /// stay stuck down
    /// ([ADR-0089](../../../../../../book/src/adr/0089-a-knobs-gesture-is-a-rate.md)).
    public Modifiers modifiers() {
        return modifiers;
    }

    /// The value the handling widget reported when this gesture began, or `NaN`
    /// when no button is held.
    ///
    /// [#dragX()] answers "how far has the pointer moved since the press"; this
    /// answers "and what was the value then". A control whose drag is a **rate**
    /// rather than a position needs both — a knob maps 200 logical pixels of
    /// vertical travel onto its whole range (§3), so where it ends up depends
    /// entirely on where it started, and a widget is a value rebuilt every frame
    /// with nowhere to keep that.
    ///
    /// The router owns it for exactly the reason it owns [#pressX()]: its
    /// implicit capture already spans the gesture ([ADR-0058]), so it is both the
    /// only thing that can know and the thing whose lifetime already matches
    /// ([ADR-0075], [ADR-0089]). What the number *means* is the widget's own
    /// business — the router asks [Handles#gestureAnchor()] on the press and
    /// hands the answer back unexamined.
    ///
    /// `NaN` rather than zero, the same convention [#dragX()] uses: arithmetic on
    /// it produces `NaN` and a comparison against it is `false`, so "there is no
    /// gesture" propagates instead of reading as "the gesture started at zero".
    public double anchor() {
        return anchor;
    }

    /// Sets [#anchor()]. **The router's**, exactly as [#localTo] is — public for
    /// the same reason, that a test building an event by hand has to be able to
    /// say what the gesture started from.
    public void anchoredAt(double value) {
        this.anchor = value;
    }

    /// The modifiers held when the button went **down**, as opposed to
    /// [#modifiers()], which is what is held now.
    ///
    /// A gesture's meaning is decided when it starts. `Shift`-dragging a knob is
    /// a fine adjustment (§3), and reading the live modifier instead would make
    /// pressing Shift halfway through a drag **rescale the travel already
    /// covered** — at 100 px down with the sensitivity going from 1 to 0.1, the
    /// value jumps from half a range below where it started to a twentieth of
    /// one. Drawn perfectly, reported nowhere, and it looks like the knob slipped
    /// ([ADR-0089]).
    ///
    /// [Modifiers#NONE] when no button is held, which is the same "there is no
    /// gesture" the `NaN`s report.
    public Modifiers gestureModifiers() {
        return gestureModifiers;
    }

    /// Sets [#gestureModifiers()]. The router's, like [#anchoredAt].
    public void gestureStartedWith(Modifiers value) {
        this.gestureModifiers = value == null ? Modifiers.NONE : value;
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
    /// Lines rather than pixels, and that is settled rather than pending: SDL
    /// reports no pixel axis, and ADR-0115 declines to go around it for one.
    /// What §2.4 wanted from "pixel-precise" is smoothness, and the fraction is
    /// where the smoothness is — this is a `float` and routinely not a whole
    /// number, because a touchpad reports parts of a detent. **Multiply by a line
    /// height**; a scroll view uses `--gb-scroll-line`.
    ///
    /// Round this and a slow trackpad scrolls in jerks or not at all. When whole
    /// clicks are what is wanted — a `select` stepping options, a knob's detent —
    /// read [#ticksY()] instead, which is accumulated rather than rounded.
    public float deltaY() {
        return deltaY;
    }

    /// [#deltaX()] accumulated into whole detents.
    public int ticksX() {
        return ticksX;
    }

    /// [#deltaY()] accumulated into whole detents — usually 0, occasionally ±1.
    ///
    /// **Not `(int) deltaY()`.** The accumulation is the platform's and it is
    /// kept across events: a touchpad dragged slowly reports a long run of
    /// fractions, each of which truncates to nothing, and one of them arrives
    /// carrying the click those fractions added up to. Truncating per event
    /// gives a control that never moves; this is a discrete counter that works
    /// on a trackpad ([ADR-0115]).
    ///
    /// A mouse wheel reports ±1 here and ±1.0 in [#deltaY()], so a control
    /// reading detents behaves the way it always has.
    public int ticksY() {
        return ticksY;
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
