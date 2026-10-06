package dev.goldberry.paint.tree;

import java.util.ArrayList;
import java.util.List;

import org.jspecify.annotations.Nullable;

import dev.goldberry.layout.FlexDirection;
import dev.goldberry.layout.Length;
import dev.goldberry.layout.Position;
import dev.goldberry.paint.Box;

/// The second rule Yoga does not implement: an absolutely positioned box whose
/// width comes from its content, capped by `max-width`, is laid out **at the
/// width it ends up with**, so that what wraps inside it is as tall as its
/// wrapped lines.
///
/// CSS calls this shrink-to-fit. The box is as wide as its content, but no
/// wider than its cap, and its children are laid out at that width. A
/// paragraph that does not fit wraps, and the box is as tall as the wrapped
/// paragraph.
///
/// Yoga gets this right when the box's parent is a column. It constrains the
/// box to its containing block and lays the content out inside the cap. When
/// the parent is a **row**, Yoga measures the box at its max-content width,
/// with no available width at all. Nothing wraps in that measure, so a long
/// paragraph is one line high. The width is then clamped to the cap, but the
/// one-line height is handed on as exact, and the children are laid out at the
/// capped width inside it: a paragraph that wraps to two lines is painted over
/// the sibling below it. `ShrinkToFitTest` records the case against the
/// compiled library.
///
/// ## The correction
///
/// After a layout pass, each box this applies to that Yoga laid out at its cap
/// is given that cap as its width, and the tree is laid out once more. With a
/// definite width Yoga lays the children out at it, and the box is as tall as
/// they are. Only the boxes the first pass laid out again are looked at, and
/// Yoga re-lays out only what the pins dirtied, so the second pass is the
/// size of the boxes it corrects.
///
/// The pin stays on the node while nothing under the box changes, which makes
/// a static frame cost nothing extra. A change anywhere under the box, or to
/// the box itself, dirties the node in Yoga. That drops the pin, and the next
/// pass measures the content again: a log line that got longer may no longer
/// fit, and a shorter one may no longer reach the cap.
///
/// A box narrower than its cap is not pinned. Its content fitted at
/// max-content, so nothing wrapped and Yoga's height is already right.
///
/// ## Which boxes
///
/// Absolutely positioned, inside a row, with an `auto` width that is not
/// derived from `left` and `right` together, and a `max-width` in points.
/// A percentage cap is left to Yoga: it resolves against the containing block,
/// which can change size without dirtying the box, and a pin would then hold
/// a width the block no longer allows.
///
/// Read more: [How layout works](https://goldberry.dev/docs/layout/index.html#flexbox-from-yoga).
final class ShrinkToFit {

    /// How far under its cap a laid-out box may come and still count as capped.
    /// Yoga rounds widths to the pixel grid, and a box clamped to 220 can come
    /// back a fraction narrower.
    private static final float ROUNDING = 0.5f;

    /// The boxes this pass will lay out again and that are not pinned yet.
    private final List<RenderObject> offered = new ArrayList<>();

    /// The width `box` is pinned to when Yoga lays it out at its cap, or
    /// [Float#NaN] when Yoga lays it out correctly by itself.
    ///
    /// @param box    the box
    /// @param parent the box it is a child of, or null for a root, which Yoga
    ///               does not lay out as an absolute child
    static float cap(Box box, @Nullable Box parent) {
        if (parent == null || box.position() != Position.ABSOLUTE || !isRow(parent.direction())) {
            return Float.NaN;
        }
        if (isDefinite(box.width())) {
            return Float.NaN;
        }
        var inset = box.inset();
        if (isDefinite(inset.left()) && isDefinite(inset.right())) {
            // Yoga derives the width from the two insets, which is definite.
            return Float.NaN;
        }
        var limits = box.limits();
        if (!(limits.maxWidth() instanceof Length.Points max)) {
            return Float.NaN;
        }
        // `min-width` wins over `max-width`, in CSS and in Yoga's clamp.
        return limits.minWidth() instanceof Length.Points min ? Math.max(max.value(), min.value()) : max.value();
    }

    /// Notes that `object`, which [#cap] applies to and which is not pinned,
    /// is about to be laid out again.
    void offer(RenderObject object) {
        offered.add(object);
    }

    /// Pins every offered box that the pass just run laid out at its cap.
    ///
    /// @return whether any was pinned, and so whether the tree has to be laid
    ///         out once more
    boolean pin() {
        var pinned = false;
        for (var object : offered) {
            pinned |= object.pinIfCapped(ROUNDING);
        }
        offered.clear();
        return pinned;
    }

    private static boolean isRow(FlexDirection direction) {
        return direction == FlexDirection.ROW || direction == FlexDirection.ROW_REVERSE;
    }

    /// Whether a length is a number Yoga resolves: points or a percentage, and
    /// not `auto` or undefined.
    private static boolean isDefinite(Length length) {
        return length instanceof Length.Points || length instanceof Length.Percent;
    }
}
