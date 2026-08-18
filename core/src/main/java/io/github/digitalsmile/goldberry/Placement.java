package io.github.digitalsmile.goldberry;

import io.github.digitalsmile.goldberry.backend.LogicalPoint;
import io.github.digitalsmile.goldberry.backend.LogicalRect;
import io.github.digitalsmile.goldberry.backend.LogicalSize;
import java.util.Objects;

/// Where a popup goes relative to the thing that opened it, and what happens
/// when that would put it off the screen.
///
/// `docs/core-widgets.md` §7 asks `popover` for "placement with flip/shift when
/// near edges", and this is that, as **arithmetic with no state**: an anchor
/// rectangle, a size, and the rectangle it all has to fit inside, in. A point,
/// out. Nothing here opens a window, reads a display or knows what a popup is —
/// which is why every case of it can be a test rather than a screenshot.
///
/// ## The three rules, in order
///
/// 1. **Preferred side.** Put it on [#side], [#gap] logical pixels away, aligned
///    along the other axis by [#align].
/// 2. **Flip** to the opposite side if it does not fit on the preferred one *and*
///    does fit on the opposite one. A dropdown near the bottom of the screen
///    opens upwards; one with room below does not, even if there is more room
///    above — a menu that jumped sides because the screen is tall would be a menu
///    nobody can predict.
/// 3. **Shift** along the cross axis until it is inside, and only then. Shifting
///    keeps the popup attached to its anchor's side while sliding it along, which
///    is what a menu near the right-hand edge should do; flipping the cross axis
///    would move it somewhere else entirely.
///
/// If it *still* does not fit — a popup taller than the screen — it is clamped to
/// the near edge, so the top of a too-long menu is visible rather than the middle
/// of it. Making it scroll is `scroll`'s job and does not exist yet.
///
/// ## One coordinate space
///
/// `anchor` and `within` must be in the same space and the answer comes back in
/// it. Which space that is, this class does not care: a caller working in a
/// window's own coordinates passes the work area translated into them, and gets a
/// point it can hand straight to a popup as an offset.
///
/// @param side  the side of the anchor to prefer
/// @param align how to line up along the other axis
/// @param gap   the distance from the anchor, in logical pixels
public record Placement(Side side, Align align, float gap) {

    /// The default: directly below the anchor, left edges lined up, 4px away —
    /// a dropdown.
    public static final Placement BELOW = new Placement(Side.BOTTOM, Align.START, 4);

    /// Above the anchor, otherwise like [#BELOW].
    public static final Placement ABOVE = new Placement(Side.TOP, Align.START, 4);

    /// To the end side, top edges lined up — a submenu.
    public static final Placement AFTER = new Placement(Side.END, Align.START, 0);

    public Placement {
        Objects.requireNonNull(side, "side");
        Objects.requireNonNull(align, "align");
        if (!Float.isFinite(gap) || gap < 0) {
            throw new IllegalArgumentException(
                    "a gap is a distance from the anchor, and " + gap + " is not one");
        }
    }

    /// This placement with a different gap.
    public Placement gap(float value) {
        return new Placement(side, align, value);
    }

    /// This placement aligned differently along the cross axis.
    public Placement align(Align value) {
        return new Placement(side, value, gap);
    }

    /// Which side of the anchor a popup sits on.
    public enum Side {

        /// Below the anchor — a dropdown.
        BOTTOM,

        /// Above it.
        TOP,

        /// Before it: the left in a left-to-right window, until §17's
        /// right-to-left work says otherwise.
        START,

        /// After it — a submenu.
        END;

        /// The side [Placement] flips to when this one does not fit.
        public Side opposite() {
            return switch (this) {
                case BOTTOM -> TOP;
                case TOP -> BOTTOM;
                case START -> END;
                case END -> START;
            };
        }

        /// Whether this side is above or below rather than beside — which decides
        /// which axis is the cross axis, and so what [Align] means.
        public boolean isVertical() {
            return this == BOTTOM || this == TOP;
        }
    }

    /// How a popup lines up with its anchor along the axis it is not offset on.
    public enum Align {

        /// Leading edges together — the usual one, because a dropdown's left edge
        /// under its control's left edge is what makes them read as one thing.
        START,

        /// Centres together.
        CENTER,

        /// Trailing edges together.
        END
    }

    /// Where a popup of `size` goes, and which side it ended up on.
    ///
    /// @param at      the top-left, in the same space as the anchor
    /// @param side    the side it is actually on, which is [Placement#side]
    ///                unless it flipped
    /// @param flipped whether rule 2 moved it to the opposite side
    /// @param shifted whether rule 3 slid it along to keep it inside
    public record Result(LogicalPoint at, Side side, boolean flipped, boolean shifted) {

        public Result {
            Objects.requireNonNull(at, "at");
            Objects.requireNonNull(side, "side");
        }
    }

    /// Applies the three rules.
    ///
    /// @param anchor the rectangle to place against — a button, a field, a menu
    ///               item
    /// @param size   the popup's size, already measured
    /// @param within the rectangle it has to stay inside, in the same coordinate
    ///               space as `anchor` — a display's work area, usually
    /// @return where to put it, and what had to be done to keep it there
    public Result place(LogicalRect anchor, LogicalSize size, LogicalRect within) {
        Objects.requireNonNull(anchor, "anchor");
        Objects.requireNonNull(size, "size");
        Objects.requireNonNull(within, "within");

        var chosen = side;
        var flipped = false;
        if (!fits(side, anchor, size, within) && fits(side.opposite(), anchor, size, within)) {
            chosen = side.opposite();
            flipped = true;
        }

        var at = along(chosen, anchor, size);
        var shifted = shift(chosen, at, size, within);
        return new Result(shifted, chosen, flipped, !shifted.equals(at));
    }

    /// Whether there is room on `candidate`'s side of the anchor, ignoring the
    /// cross axis — which rule 3 takes care of separately.
    private boolean fits(Side candidate, LogicalRect anchor, LogicalSize size, LogicalRect within) {
        return switch (candidate) {
            case BOTTOM -> anchor.bottom() + gap + size.height() <= within.bottom();
            case TOP -> anchor.top() - gap - size.height() >= within.top();
            case START -> anchor.left() - gap - size.width() >= within.left();
            case END -> anchor.right() + gap + size.width() <= within.right();
        };
    }

    /// The unclamped position on `chosen`'s side, aligned by [#align].
    private LogicalPoint along(Side chosen, LogicalRect anchor, LogicalSize size) {
        if (chosen.isVertical()) {
            var y = chosen == Side.BOTTOM
                    ? anchor.bottom() + gap
                    : anchor.top() - gap - size.height();
            return new LogicalPoint(cross(anchor.left(), anchor.width(), size.width()), y);
        }
        var x = chosen == Side.END
                ? anchor.right() + gap
                : anchor.left() - gap - size.width();
        return new LogicalPoint(x, cross(anchor.top(), anchor.height(), size.height()));
    }

    private float cross(float anchorStart, float anchorExtent, float popupExtent) {
        return switch (align) {
            case START -> anchorStart;
            case CENTER -> anchorStart + (anchorExtent - popupExtent) / 2;
            case END -> anchorStart + anchorExtent - popupExtent;
        };
    }

    /// Slides `at` along the cross axis until the popup is inside `within`,
    /// clamping to the near edge when it cannot be.
    ///
    /// The near edge and not the far one: a popup too big for the screen has to
    /// lose an end, and the end nobody minds losing is the one furthest from
    /// where it started.
    private static LogicalPoint shift(Side chosen, LogicalPoint at, LogicalSize size,
            LogicalRect within) {
        if (chosen.isVertical()) {
            return new LogicalPoint(
                    clamp(at.x(), size.width(), within.left(), within.right()), at.y());
        }
        return new LogicalPoint(
                at.x(), clamp(at.y(), size.height(), within.top(), within.bottom()));
    }

    private static float clamp(float start, float extent, float low, float high) {
        var end = start + extent;
        if (end > high) {
            start -= end - high;
        }
        return Math.max(start, low);
    }
}
