package io.github.digitalsmile.goldberry.paint.cull;

import io.github.digitalsmile.goldberry.css.value.Affine;
import io.github.digitalsmile.goldberry.paint.Clip;

/// The rectangle a box — or a whole subtree of them — actually puts ink in.
///
/// Four **edges** rather than an origin and a size, for [Clip]'s reason exactly:
/// every operation here is a union or an intersection, and both are `min`/`max`
/// over edges with no width to add back on. It is [Clip]'s mirror image — a clip
/// says what *may* be drawn and this says what *would* be — and the one question
/// asked of the pair is whether they overlap at all.
///
/// ## What it is for
///
/// Painting a box that lands entirely outside the clip in force costs a native
/// call, a path transformation and, for text, a glyph run — and produces nothing.
/// A window with a long viewport in it is mostly that: the icon sheet is 1544
/// tiles of which forty are on screen, and before this every one of the other
/// 1504 was submitted to Blend2D to be clipped away (ADR-0313).
///
/// The **subtree's** extent and not the node's own, because a child may be drawn
/// outside its parent — flexbox allows a box to overflow, a `transform` moves one
/// out from under its parent, and a focus ring is drawn outside the border box by
/// design. Culling on the parent's own rectangle would take those with it.
///
/// @param left   the leftmost logical pixel drawn
/// @param top    the topmost
/// @param right  one past the rightmost
/// @param bottom one past the bottommost
public record Ink(double left, double top, double right, double bottom) {

    /// A subtree that draws nothing anywhere — the identity of [#union].
    ///
    /// Inverted rather than zero-sized: a union with it must be the other
    /// operand, and a rectangle at the origin would drag every union towards
    /// (0, 0) and defeat the whole thing.
    public static final Ink NONE = new Ink(
            Double.POSITIVE_INFINITY, Double.POSITIVE_INFINITY,
            Double.NEGATIVE_INFINITY, Double.NEGATIVE_INFINITY);

    /// The ink of a rectangle at `(x, y)` with this size.
    public static Ink of(double x, double y, double width, double height) {
        return new Ink(x, y, x + width, y + height);
    }

    /// Whether this covers no pixel at all, which is [#NONE] and a zero-sized
    /// box.
    public boolean isEmpty() {
        return !(right > left) || !(bottom > top);
    }

    /// The smallest rectangle covering both — how a parent takes in a child.
    public Ink union(Ink other) {
        if (other.isEmpty()) {
            return this;
        }
        if (isEmpty()) {
            return other;
        }
        return new Ink(
                Math.min(left, other.left), Math.min(top, other.top),
                Math.max(right, other.right), Math.max(bottom, other.bottom));
    }

    /// This moved by `dx`, `dy` — a child's ink expressed in its parent's
    /// coordinates.
    public Ink shiftedBy(double dx, double dy) {
        if (isEmpty() || (dx == 0 && dy == 0)) {
            return this;
        }
        return new Ink(left + dx, top + dy, right + dx, bottom + dy);
    }

    /// This mapped through `matrix`, as the smallest axis-aligned rectangle that
    /// contains the result.
    ///
    /// All four corners, because a rotation turns a rectangle into one that is
    /// not axis-aligned and two corners would miss half of it. Conservative for
    /// a rotation and exact for the translations and scales that are the only
    /// transforms a scrolling tree actually has — and conservative is the safe
    /// direction here: too large a rectangle draws something that was going to be
    /// clipped anyway, and too small a one loses it.
    public Ink mappedBy(Affine matrix) {
        if (matrix.isIdentity() || isEmpty()) {
            return this;
        }
        var minX = Double.POSITIVE_INFINITY;
        var minY = Double.POSITIVE_INFINITY;
        var maxX = Double.NEGATIVE_INFINITY;
        var maxY = Double.NEGATIVE_INFINITY;
        for (var corner = 0; corner < 4; corner++) {
            var x = (corner == 0 || corner == 3) ? left : right;
            var y = corner < 2 ? top : bottom;
            var mappedX = matrix.mapX(x, y);
            var mappedY = matrix.mapY(x, y);
            minX = Math.min(minX, mappedX);
            minY = Math.min(minY, mappedY);
            maxX = Math.max(maxX, mappedX);
            maxY = Math.max(maxY, mappedY);
        }
        return new Ink(minX, minY, maxX, maxY);
    }

    /// Whether any of this could land inside `clip`.
    ///
    /// The one question the painter asks. **False is the licence to skip a whole
    /// subtree**, so it is answered conservatively: an empty ink overlaps
    /// nothing, and everything else is the ordinary four comparisons against a
    /// rectangle that is infinite when nothing clips.
    ///
    /// Touching edges do not overlap — a box whose right edge is exactly the
    /// clip's left puts no pixel inside it.
    public boolean overlaps(Clip clip) {
        if (isEmpty()) {
            return false;
        }
        return right > clip.left() && left < clip.right() && bottom > clip.top() && top < clip.bottom();
    }
}
