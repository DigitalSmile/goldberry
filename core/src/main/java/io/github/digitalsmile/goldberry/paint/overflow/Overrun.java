package io.github.digitalsmile.goldberry.paint.overflow;

import java.util.Objects;

import io.github.digitalsmile.goldberry.render.model.LogicalRect;

/// A box that did not fit the box it was laid out in.
///
/// Not an error and not a paint decision: overflow is CSS's behaviour, and a
/// window narrower than its content is *supposed* to overflow rather than
/// deform. What this records is that it happened and where, because a control
/// pushed off the edge of a window looks exactly like a control that was never
/// built ([ADR-0375]).
///
/// @param container what the child was laid out in — a type name, an id, or
///                  `"a box"` for a node the widget layer did not name
/// @param child     the same for the box that overran it
/// @param overrunX  how far past the container's right edge, in logical pixels,
///                  or 0 when the overrun is not on this axis
/// @param overrunY  the same downwards
public record Overrun(String container, String child, float overrunX, float overrunY) {

    public Overrun {
        Objects.requireNonNull(container, "container");
        Objects.requireNonNull(child, "child");
    }

    /// The overrun of `child` inside `parent`, or null when it fits.
    ///
    /// Both rectangles are in the **parent's** coordinates, which is what Yoga
    /// reports.
    ///
    /// ## Three things that are not an overrun
    ///
    /// Each of these was measured rather than supposed. Running the showcase's
    /// whole suite produced 688 reports; 665 of them were one of these, and the
    /// 23 that were left were real ([ADR-0394]).
    ///
    /// **A container with no size.** A `0 × 0` box is an anchor that placed
    /// children hang off, not a box anything could fit inside — every child
    /// overruns it by its own whole size. A slider's ticks are the case: they sit
    /// around a zero-width mark.
    ///
    /// **A child that starts outside.** Flow never produces a negative offset: a
    /// flowed child begins at its container's content origin. A child at `-6` was
    /// *put* there by insets or a margin, which is the same "placed rather than
    /// flowed" exemption [#between]'s caller already makes for an absolute box.
    /// A slider's thumb is centred across a four-pixel groove and hangs six
    /// pixels out of it on both sides by design.
    ///
    /// **A pixel or two.** Layout is rounded onto the device pixel grid and a
    /// line box may be shorter than the face's natural leading — CSS allows
    /// `line-height` tighter than the text in it, and text that overhangs its
    /// line box is the normal consequence rather than a defect. The measured
    /// distribution has a cliff exactly there: 385 reports overran by more than
    /// one pixel and only 23 by more than two.
    public static Overrun between(String container, String child, LogicalRect parent, LogicalRect box) {
        if (parent.size().width() <= 0 || parent.size().height() <= 0) {
            return null;
        }
        if (box.left() < 0 || box.top() < 0) {
            return null;
        }
        var x = box.right() - parent.size().width();
        var y = box.bottom() - parent.size().height();
        if (x <= TOLERANCE && y <= TOLERANCE) {
            return null;
        }
        return new Overrun(container, child, Math.max(0, x), Math.max(0, y));
    }

    /// How far past an edge is still "fits", in logical pixels.
    ///
    /// Two, and the third paragraph above is where the number comes from. It is
    /// not a tuning knob: what this diagnostic exists to catch is a control
    /// pushed off the edge of a window, which is tens of pixels, and what it kept
    /// catching instead was arithmetic.
    private static final float TOLERANCE = 2.0f;

    /// One line, naming both boxes and the distance.
    @Override
    public String toString() {
        var axes = overrunX > 0 && overrunY > 0
                ? overrunX + " wide and " + overrunY + " tall"
                : overrunX > 0 ? overrunX + " wide" : overrunY + " tall";
        return child + " overruns " + container + " by " + axes;
    }
}
