package dev.goldberry.paint.tree;

import java.util.Objects;

import org.jspecify.annotations.Nullable;

import dev.goldberry.paint.Clip;
import dev.goldberry.paint.filter.EdgeExtension;
import dev.goldberry.render.model.LogicalSize;

/// The part of a raster a `backdrop-filter` draws again and filters: the box,
/// out by as far as its filters read, on whole logical pixels.
///
/// Cut to the raster, since nothing is drawn past it; where that cut what the
/// filters wanted to read, [#edges] says how far the edge pixels are repeated
/// instead. Cut also to what can be seen, out by the same reach, because a box
/// in a scroll view half out of sight changes nothing there.
///
/// @param left   in the raster's logical coordinates
/// @param top    in the same
/// @param right  one past the rightmost
/// @param bottom one past the bottommost
/// @param edges  physical pixels to repeat past each edge the raster cut
record BackdropRegion(double left, double top, double right, double bottom, EdgeExtension edges) {

    BackdropRegion {
        Objects.requireNonNull(edges, "edges");
    }

    /// The region for a box whose border box covers `shape` in the raster.
    ///
    /// @param shape   the border box's bounding rectangle in the raster, through
    ///                the box's matrix
    /// @param reach   how far the filters read past it: `left`, `top`, `right`,
    ///                `bottom`, logical pixels
    /// @param raster  the raster's size
    /// @param visible what of the raster can be seen, or [Clip#NONE]
    /// @param factor  physical pixels per logical one
    /// @return null when none of the box can be seen
    static @Nullable BackdropRegion of(Clip shape, double[] reach, LogicalSize raster, Clip visible, double factor) {
        Objects.requireNonNull(shape, "shape");
        Objects.requireNonNull(reach, "reach");
        Objects.requireNonNull(raster, "raster");
        Objects.requireNonNull(visible, "visible");
        var seen = shape.intersect(visible).intersect(Clip.of(0, 0, raster.width(), raster.height()));
        if (shape.isEmpty() || seen.isEmpty()) {
            return null;
        }
        var wantLeft = Math.floor(shape.left() - reach[0]);
        var wantTop = Math.floor(shape.top() - reach[1]);
        var wantRight = Math.ceil(shape.right() + reach[2]);
        var wantBottom = Math.ceil(shape.bottom() + reach[3]);
        var width = Math.ceil(raster.width());
        var height = Math.ceil(raster.height());
        var left = Math.max(0, wantLeft);
        var top = Math.max(0, wantTop);
        var right = Math.min(width, wantRight);
        var bottom = Math.min(height, wantBottom);
        var edges = new EdgeExtension(
                pixels(left - wantLeft, factor),
                pixels(top - wantTop, factor),
                pixels(wantRight - right, factor),
                pixels(wantBottom - bottom, factor));
        if (!visible.isNone()) {
            // What can be seen, out by the reach: what lies further out cannot
            // reach a pixel anybody sees.
            left = Math.max(left, Math.floor(visible.left() - reach[0]));
            top = Math.max(top, Math.floor(visible.top() - reach[1]));
            right = Math.min(right, Math.ceil(visible.right() + reach[2]));
            bottom = Math.min(bottom, Math.ceil(visible.bottom() + reach[3]));
        }
        if (!(right > left) || !(bottom > top)) {
            return null;
        }
        return new BackdropRegion(left, top, right, bottom, edges);
    }

    /// The width, in logical pixels.
    double width() {
        return right - left;
    }

    /// The height, in logical pixels.
    double height() {
        return bottom - top;
    }

    private static int pixels(double logical, double factor) {
        return logical > 0 ? (int) Math.ceil(logical * factor) : 0;
    }
}
