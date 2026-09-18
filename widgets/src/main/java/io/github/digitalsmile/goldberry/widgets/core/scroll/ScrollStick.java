package io.github.digitalsmile.goldberry.widgets.core.scroll;

/// Whether a [Scroll] is **at the end**, and therefore whether the next message
/// should carry it along.
///
/// ## The whole widget turns on this one boolean
///
/// [ScrollAnchor#END] promises two different things of the same viewport: a row
/// arriving while the reader is at the bottom scrolls, and a row arriving while
/// they are reading history does not move them. Nothing about the row tells the
/// two apart — it is the same row. What tells them apart is where the viewport
/// *was* when it arrived, so that is what this remembers.
///
/// Kept as a flag rather than recomputed when it is wanted, because by the time
/// a row has arrived the extents have already changed: once the content is
/// taller, `offset == overflow` is false for a viewport that was at the end a
/// moment ago and is the whole question. The flag is written whenever the offset
/// moves for any reason, which is the one moment the comparison is meaningful.
///
/// ## How close is at the end
///
/// [#TOLERANCE] logical pixels — half of one, which is the same figure
/// [ScrollController.Position#canScrollRight] has used since it was written and
/// is below a device pixel at every scale this toolkit renders at. It exists
/// because an offset is a `double` arrived at by adding wheel fractions and
/// clamping, and asking a viewport that has been dragged to its bottom for exact
/// equality with an overflow computed from float extents loses the stick roughly
/// whenever the arithmetic feels like it.
///
/// It is deliberately *small*. A pixel up is a decision — the reader stopped
/// following — and the frame after it the stick is off and stays off until they
/// come back down. There is no hysteresis and no grace period: a tolerance wide
/// enough to be forgiving is wide enough to drag somebody back down while they
/// are reading.
final class ScrollStick {

    /// How near the end counts as at it, in logical pixels.
    static final double TOLERANCE = 0.5;

    private boolean x;
    private boolean y;

    /// Starts a viewport at the end on both axes, for [ScrollAnchor#END].
    ///
    /// Before the first layout, so the very first measurement is treated as a
    /// viewport that was already at the end and lands there — which is what
    /// "opens at the end" is, with no separate first-frame case to get wrong.
    void openAtEnd() {
        x = true;
        y = true;
    }

    /// Re-reads the flags from where the viewport is now.
    ///
    /// Called after every move, whatever caused it: a wheel, a key, a drag, a
    /// track click, a `scrollIntoView` or the stick's own correction. One place,
    /// so there is no path that moves a viewport and leaves this lying.
    void moved(double offsetX, double offsetY, double overflowX, double overflowY) {
        x = offsetX >= overflowX - TOLERANCE;
        y = offsetY >= overflowY - TOLERANCE;
    }

    /// Whether the viewport is at the right-hand end — at the **far** end along
    /// the scrolling direction, which under a right-to-left layout is the left of
    /// the screen: an offset is measured from the content's start edge and the
    /// direction decides which edge that is.
    boolean atEndX() {
        return x;
    }

    /// Whether it is at the bottom.
    boolean atEndY() {
        return y;
    }
}
