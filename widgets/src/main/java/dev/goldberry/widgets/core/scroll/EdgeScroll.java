package dev.goldberry.widgets.core.scroll;

import org.jspecify.annotations.Nullable;

import dev.goldberry.render.model.LogicalRect;

/// A drag held at the edge of a viewport, carrying the viewport on with it.
///
/// What a selection does when it is dragged to the bottom of a pane: the pane keeps
/// scrolling for as long as the button is down and the pointer stays there, faster
/// the further past the edge it is, and whatever is selecting follows the content
/// that arrives under the pointer. Both content views and `text-area` want it, and
/// neither had it — a drag stopped selecting at the edge, because nothing moves the
/// viewport while a pointer that is held still sends no events ([ADR-0500]).
///
/// ```java
/// // on the press
/// edge.hold(scope::nudge, scope.axis());
/// edge.pointer(x, y, viewport);
/// // on every drag
/// edge.pointer(x, y, viewport);
/// select(edge.x(), edge.y());
/// // in `render`, which is where the frame clock is
/// if (edge.tick(context.nowMillis())) { … }
/// // and `isAnimating()` is `edge.isScrolling()`
/// ```
///
/// ## A timer plus a clamp, and the timer is the frame clock
///
/// The timer is not a timer. A widget is handed the clock in `render` and keeps the
/// frames coming by answering `isAnimating()` (ADR-0081), which is how a glide and a
/// fading bar already move ([ScrollGlide], [ScrollFade]) — so the distance a frame
/// moves is **speed × the time since the last frame**, and a 144 Hz panel scrolls
/// exactly as far per second as a 60 Hz one. Only while there is somewhere to go:
/// a viewport that refuses a step, because it is already at its end, is left alone
/// until the pointer moves again, and the loop goes back to sleep.
///
/// The clamp is [#x()] and [#y()]: the pointer **pulled back inside** the viewport.
/// Whatever is selecting asks what is under that point rather than under the
/// pointer, because the pointer is past the edge and nothing is under it there — a
/// document's words are clipped to the viewport and answer nothing outside it, which
/// is the whole of why a drag used to stop.
///
/// ## The speed is the pointer's, and never the wheel's
///
/// The entry that asked for this called the touchpad the interesting part, and the
/// answer is to keep the two apart:
///
/// - **A wheel during a drag scrolls the way a wheel always does**, one-to-one and in
///   the viewport's own units, and the selection follows what it brings in. It does
///   not speed this up, slow it down, start it or stop it. A touchpad's two-finger
///   scroll arrives as a stream of fractional wheel deltas, and letting those feed a
///   velocity would turn a precise gesture into one with momentum nobody asked for.
/// - **The speed comes from the distance past the edge and nothing else** — see
///   [#speed(double)]. It is a position, so it is the same on a mouse, a pen and a
///   touchpad, and a touchpad's sub-pixel pointer motion moves it by sub-pixel amounts.
/// - **The steps are fractional and are not rounded.** At the edge this moves 160
///   logical pixels a second, which at 144 Hz is 1.1 a frame, and just inside the
///   band it is a small fraction of one. Rounding each step would stop a slow edge
///   dead, and saving the fractions up for a whole pixel would move it in jerks. An
///   offset is a `double` already, and the painter places a fractional translation
///   like any other.
public final class EdgeScroll {

    /// How far inside the viewport the edge begins, in logical pixels.
    ///
    /// A band rather than the edge itself, because a pane that fills a maximised
    /// window has nothing below it the pointer can reach: the screen ends where the
    /// viewport does. Sixteen is about one line of body text.
    public static final double BAND = 16;

    /// How much faster it goes for each logical pixel further past the band's inner
    /// edge, in logical pixels a second.
    ///
    /// Ten, so the viewport's own edge — [#BAND] in — moves 160 a second, eight lines
    /// of body text, which is slow enough to stop on a word; a hundred pixels past it
    /// moves a screenful a second.
    public static final double GAIN = 10;

    /// The fastest it goes, in logical pixels a second — reached 240 pixels in.
    ///
    /// A cap because the distance a pointer can travel past a pane is the size of the
    /// screen, and a document flying past at the speed that would imply is one nobody
    /// can stop on anything.
    public static final double MAX_SPEED = 2400;

    /// The longest step one frame may take, in milliseconds.
    ///
    /// A frame that arrives late — a window being dragged, a paint that stalled on a
    /// font — would otherwise move the viewport by everything it had missed at once,
    /// which is a jump of a page. Three frames at 60 Hz: long enough that an ordinary
    /// hitch is still paid for, short enough that a stall is not.
    static final double LONGEST_STEP_MILLIS = 50;

    /// What moves when the edge is held: a viewport's offset, by a distance.
    ///
    /// A **distance**, like [ScrollController#scrollBy], and applied **at once** —
    /// unlike it, this is direct input and must not glide ([ScrollScope#nudge]).
    @FunctionalInterface
    public interface Target {

        /// Moves by `dx`, `dy` logical pixels, positive right and down, clamped to
        /// what there is to show.
        ///
        /// @return whether anything moved — false at the end, which is what stops the
        ///         frames
        boolean scrollBy(double dx, double dy);
    }

    /// Nothing held, which is every edge between drags. One per thing that can be
    /// dragged in, kept for its life — a state's field.
    public EdgeScroll() {}

    private @Nullable Target target;

    private ScrollAxis axes = ScrollAxis.BOTH;

    /// The viewport, in the same coordinates as the pointer, or null before anybody
    /// has said where it is.
    private @Nullable LogicalRect viewport;

    private double pointerX;

    private double pointerY;

    private boolean held;

    /// Whether the viewport refused the last step, so the frames can stop until the
    /// pointer moves.
    private boolean stalled;

    /// The frame time of the last step, or NaN when no run is under way — so the first
    /// frame of a run starts the clock rather than moving by however long ago the last
    /// run ended.
    private double last = Double.NaN;

    /// Takes hold on a press.
    ///
    /// @param target what to move, or null when the press is in no viewport — which is
    ///        an ordinary answer: the clamp still works and nothing scrolls
    /// @param axes which way it may move; a step along the other axis is never asked
    ///        for, so a vertical pane does not wake up because the pointer is near its
    ///        side
    public void hold(@Nullable Target target, ScrollAxis axes) {
        this.target = target;
        this.axes = axes;
        held = true;
        stalled = false;
        last = Double.NaN;
    }

    /// Where the pointer is, and where the viewport is — on the press and on every
    /// drag.
    ///
    /// A pointer that moved is also a pointer that might have somewhere to go again,
    /// so a stall is forgotten here.
    public void pointer(double x, double y, @Nullable LogicalRect viewport) {
        pointerX = x;
        pointerY = y;
        if (viewport != null) {
            this.viewport = viewport;
        }
        stalled = false;
    }

    /// Where the pointer is, against the viewport [#viewport(LogicalRect)] last said —
    /// for a caller that is told the viewport separately, as a `Located` node is.
    public void pointer(double x, double y) {
        pointer(x, y, null);
    }

    /// Where the viewport now is, when it moved without the pointer moving.
    public void viewport(LogicalRect viewport) {
        this.viewport = viewport;
    }

    /// Lets go — the release, or the focus leaving mid-drag.
    public void release() {
        held = false;
        target = null;
        last = Double.NaN;
    }

    /// Whether a drag is under way.
    public boolean isHeld() {
        return held;
    }

    /// Whether this wants another frame: held, past the edge, and with somewhere to go.
    ///
    /// What an `isAnimating()` answers with. False once the viewport has refused a
    /// step, so a pointer resting below the end of a document does not keep the frame
    /// loop awake at the display's rate for nothing.
    public boolean isScrolling() {
        return held && target != null && !stalled && (velocityX() != 0 || velocityY() != 0);
    }

    /// One frame: moves the viewport by the speed times the time since the last one.
    ///
    /// @param nowMillis the frame clock —
    ///        [dev.goldberry.widget.style.Paints.Context#nowMillis()]
    /// @return whether the viewport moved, so the caller knows the content under
    ///         [#x()], [#y()] is about to be different
    public boolean tick(double nowMillis) {
        var target = this.target;
        if (!isScrolling() || target == null) {
            last = Double.NaN;
            return false;
        }
        if (Double.isNaN(last)) {
            // The first frame of a run says when it began and moves nothing -- the
            // way a glide is stamped (ADR-0363). The distance is a rate times a time,
            // and until two frames have happened there is no time.
            last = nowMillis;
            return false;
        }
        var step = Math.min(nowMillis - last, LONGEST_STEP_MILLIS);
        last = nowMillis;
        if (step <= 0) {
            return false;
        }
        var moved = target.scrollBy(velocityX() * step / 1000, velocityY() * step / 1000);
        if (!moved) {
            stalled = true;
            last = Double.NaN;
        }
        return moved;
    }

    /// The pointer's x, pulled back inside the viewport — where to ask what is under
    /// the pointer.
    public double x() {
        var rect = viewport;
        return rect == null ? pointerX : clamp(pointerX, rect.left(), rect.right());
    }

    /// The pointer's y, pulled back inside the viewport.
    public double y() {
        var rect = viewport;
        return rect == null ? pointerY : clamp(pointerY, rect.top(), rect.bottom());
    }

    private double velocityX() {
        var rect = viewport;
        return rect == null || !axes.isHorizontal() ? 0 : velocity(pointerX, rect.left(), rect.right());
    }

    private double velocityY() {
        var rect = viewport;
        return rect == null || !axes.isVertical() ? 0 : velocity(pointerY, rect.top(), rect.bottom());
    }

    /// How fast, in logical pixels a second, for a pointer `depth` pixels past the
    /// inner edge of the band.
    ///
    /// Nothing at or before it, then [#GAIN] a second for every pixel, up to
    /// [#MAX_SPEED]. **Linear**, because the pointer is the throttle and a throttle
    /// whose response curves is one a hand has to learn; the cap is what stops it
    /// running away.
    public static double speed(double depth) {
        if (!(depth > 0)) {
            return 0;
        }
        return Math.min(MAX_SPEED, depth * GAIN);
    }

    /// Which way and how fast along one axis, for a pointer at `pointer` over a
    /// viewport running from `near` to `far` — negative towards `near`.
    ///
    /// The band is [#BAND] or an eighth of the viewport, whichever is less, so a
    /// three-line `text-area` still has a middle to drag in: a sixteen-pixel band at
    /// each end of a sixty-pixel box would leave it half edge.
    public static double velocity(double pointer, double near, double far) {
        var extent = far - near;
        if (!(extent > 0)) {
            return 0;
        }
        var band = Math.min(BAND, extent / 8);
        if (pointer < near + band) {
            return -speed(near + band - pointer);
        }
        if (pointer > far - band) {
            return speed(pointer - (far - band));
        }
        return 0;
    }

    /// `pointer` pulled inside `near`..`far`, and a pixel short of the far edge.
    ///
    /// Short of it because the far edge belongs to whatever is next: a rectangle's
    /// right and bottom are exclusive to the router's hit test, and a point exactly on
    /// the bottom of a viewport is not in it.
    public static double clamp(double pointer, double near, double far) {
        return Math.clamp(pointer, near, Math.max(near, far - 1));
    }
}
