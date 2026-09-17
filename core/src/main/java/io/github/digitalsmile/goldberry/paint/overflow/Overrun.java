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
    /// reports. A fractional overrun of less than a tenth of a pixel is not one:
    /// a percentage width and a rounded layout disagree in the last digit on
    /// almost every frame, and a diagnostic that fires on those says nothing.
    public static Overrun between(String container, String child, LogicalRect parent, LogicalRect box) {
        var x = box.right() - parent.size().width();
        var y = box.bottom() - parent.size().height();
        if (x < TOLERANCE && y < TOLERANCE) {
            return null;
        }
        return new Overrun(container, child, Math.max(0, x), Math.max(0, y));
    }

    /// How far past an edge is still "fits", in logical pixels.
    private static final float TOLERANCE = 0.1f;

    /// One line, naming both boxes and the distance.
    @Override
    public String toString() {
        var axes = overrunX > 0 && overrunY > 0
                ? overrunX + " wide and " + overrunY + " tall"
                : overrunX > 0 ? overrunX + " wide" : overrunY + " tall";
        return child + " overruns " + container + " by " + axes;
    }
}
