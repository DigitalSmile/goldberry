package io.github.digitalsmile.goldberry.layout;

import io.github.digitalsmile.goldberry.css.Affine;

/// The rectangle a subtree is confined to, in the frame's logical coordinates.
///
/// Held as four **edges** rather than an origin and a size, because every
/// operation on it is an intersection and intersecting edges is four `min`/`max`
/// calls against arithmetic that never has to add a width back on.
///
/// ## Why the painter carries this in Java
///
/// Blend2D has `bl_context_clip_to_rect_d` and `bl_context_restore_clipping`, and
/// the second of those goes back to **the whole surface** — not to whatever clip
/// was in force before. `bl_context_save` / `bl_context_restore` are not on the
/// export list, so there is no clip *stack* down there to push onto. A nested
/// scroll view therefore cannot be expressed by clipping twice and unclipping
/// once: the inner viewport ending would take the outer one's clip off with it,
/// and the rest of the outer scroller's content would paint over everything
/// beside it.
///
/// So the stack lives here. The painter accumulates the intersection on the way
/// down, and every change is `resetClip()` followed by one `clipTo` of the
/// accumulated rectangle — which is the same argument ADR-0068 made for the
/// transform stack, arrived at from the other end. There it was so hit testing
/// could invert the matrix; here it is because the native side offers no way to
/// undo one clip without undoing all of them
/// ([ADR-0114](../../../../../../book/src/adr/0114-a-clip-is-a-rectangle-the-painter-carries.md)).
///
/// @param left   the leftmost logical pixel drawn
/// @param top    the topmost
/// @param right  one past the rightmost
/// @param bottom one past the bottommost
public record Clip(double left, double top, double right, double bottom) {

    /// A clip that admits everything, which is what a tree with no clipping box
    /// in it is painted under.
    ///
    /// Infinite rather than "the frame", because the painter does not know how
    /// big the frame is at the point it composes these and an intersection with
    /// an infinite rectangle is the identity — so the common case costs the same
    /// four comparisons as any other and needs no null check.
    public static final Clip NONE = new Clip(
            Double.NEGATIVE_INFINITY, Double.NEGATIVE_INFINITY,
            Double.POSITIVE_INFINITY, Double.POSITIVE_INFINITY);

    /// The clip of a box at `(x, y)` with this size.
    public static Clip of(double x, double y, double width, double height) {
        return new Clip(x, y, x + width, y + height);
    }

    /// This clip narrowed by `other` — the only way one is ever combined.
    ///
    /// Intersection and not union, because a clip is a promise about what
    /// *cannot* be drawn: a child inside a scroll view inside a dialog is
    /// confined by both, and neither can widen the other.
    public Clip intersect(Clip other) {
        return new Clip(
                Math.max(left, other.left), Math.max(top, other.top),
                Math.min(right, other.right), Math.min(bottom, other.bottom));
    }

    /// This clip mapped through `matrix`, as the smallest axis-aligned
    /// rectangle that contains the result.
    ///
    /// **Exact for a translation or a scale, and conservative for a rotation.**
    /// The four corners are mapped and their bounding box taken, so a viewport
    /// rotated 45° clips to the square around its diamond and lets a little
    /// content show in the corners. That is the honest cost of a rectangular
    /// clip: Blend2D's is a rectangle, and the alternative — clipping to a path
    /// — is a different native call with a different cost that nothing in the
    /// catalog has asked for. Every scroll view in the canon is axis-aligned, so
    /// the exact case is the only one that has ever run.
    public Clip map(Affine matrix) {
        if (matrix.isIdentity() || isNone()) {
            return this;
        }
        var minX = Double.POSITIVE_INFINITY;
        var minY = Double.POSITIVE_INFINITY;
        var maxX = Double.NEGATIVE_INFINITY;
        var maxY = Double.NEGATIVE_INFINITY;
        for (var i = 0; i < 4; i++) {
            var x = (i == 0 || i == 3) ? left : right;
            var y = i < 2 ? top : bottom;
            var mappedX = matrix.mapX(x, y);
            var mappedY = matrix.mapY(x, y);
            minX = Math.min(minX, mappedX);
            minY = Math.min(minY, mappedY);
            maxX = Math.max(maxX, mappedX);
            maxY = Math.max(maxY, mappedY);
        }
        return new Clip(minX, minY, maxX, maxY);
    }

    /// Whether this clip confines anything at all.
    public boolean isNone() {
        return equals(NONE);
    }

    /// Whether this clip admits nothing — a subtree scrolled entirely out of its
    /// viewport, which the painter can skip rather than draw.
    public boolean isEmpty() {
        return !(right > left) || !(bottom > top);
    }

    /// Whether `(x, y)` is inside — what hit testing asks.
    ///
    /// Half-open on the right and bottom, matching
    /// [io.github.digitalsmile.goldberry.input.HitTest.Region#contains], so a
    /// pointer exactly on the boundary between two adjacent viewports lands in
    /// exactly one of them.
    public boolean contains(double x, double y) {
        return x >= left && x < right && y >= top && y < bottom;
    }

    public double width() {
        return right - left;
    }

    public double height() {
        return bottom - top;
    }
}
