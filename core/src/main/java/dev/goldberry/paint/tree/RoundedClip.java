package dev.goldberry.paint.tree;

import org.jspecify.annotations.Nullable;

import dev.goldberry.css.Corners;
import dev.goldberry.paint.Box;
import dev.goldberry.paint.BoxEdges;
import dev.goldberry.render.model.LogicalRect;

/// What a box with `overflow` and a `border-radius` clips its children to,
/// measured from the box's own top-left corner.
///
/// Two shapes, because the toolkit's clip and CSS's are not the same one. The
/// **rectangle** is the content box — the box less its padding — which is where
/// [RenderTree] has always confined children, so that rows scrolling under a
/// viewport's padding keep its edge crisp; here it is also kept inside the
/// border. The **outline** is CSS's: the padding box, inside the border, with
/// each corner's radius reduced by the border it meets. Children are drawn
/// inside the first and lose what lies outside the second, so a 34-pixel round
/// avatar with no padding and no border shows its picture as a circle, a ringed
/// one shows it as the circle inside the ring, and a padded card clips its
/// content to the padding's rectangle and rounds it only where the curve
/// reaches in that far.
///
/// @param content the rectangle children are drawn inside: the content box,
///                kept inside the border
/// @param outline the padding box, whose rounded corners are cut away
/// @param corners the padding box's radii — the box's own, less its border
record RoundedClip(Area content, Area outline, Corners corners) {

    /// A rectangle relative to the box's top-left corner.
    record Area(double left, double top, double width, double height) {}

    /// The rounded clip `box` puts on its children, or null when a rectangle is
    /// all it needs: a border at least as wide as the radius leaves the padding
    /// box square, padding wider than the radius keeps the children out of the
    /// corners, and a box with no area has nothing to round.
    static @Nullable RoundedClip of(Box box, LogicalRect layout) {
        var width = (double) layout.width();
        var height = (double) layout.height();
        var border = box.decoration().border();
        var top = border.top().width();
        var right = border.right().width();
        var bottom = border.bottom().width();
        var left = border.left().width();
        var outer = box.decoration().corners().fittedTo(width, height);
        // A corner's inner radius is the outer one less the border it meets,
        // each axis by its own side, as CSS draws it: an ellipse where the two
        // sides differ.
        var corners = outer.inset(top, right, bottom, left);
        var outline = new Area(left, top, width - left - right, height - top - bottom);
        if (corners.isSquare() || !(outline.width() > 0) || !(outline.height() > 0)) {
            return null;
        }
        // The same rectangle `RenderTree` clips to, so the two cannot disagree
        // about where the content box is: inside the border and the padding.
        var edges = BoxEdges.content(box, width, height);
        var content = new Area(edges.left(), edges.top(), width - edges.horizontal(), height - edges.vertical());
        if (!(content.width() > 0) || !(content.height() > 0) || !reachesACorner(content, outline, corners)) {
            return null;
        }
        return new RoundedClip(content, outline, corners);
    }

    /// Whether anything drawn inside `content` can land in a corner the curve
    /// cuts off.
    ///
    /// Padding wider than the radius keeps every child clear of the corners, so
    /// the rectangle already is the whole clip and there is nothing to cut — a
    /// text field, padded twelve pixels in from a six-pixel radius, is the
    /// common case, and it keeps the walk it always had with no layer.
    private static boolean reachesACorner(Area content, Area outline, Corners corners) {
        var right = outline.left() + outline.width();
        var bottom = outline.top() + outline.height();
        var contentRight = content.left() + content.width();
        var contentBottom = content.top() + content.height();
        var tl = corners.topLeftRadius();
        var tr = corners.topRightRadius();
        var br = corners.bottomRightRadius();
        var bl = corners.bottomLeftRadius();
        return (!tl.isSquare() && content.left() < outline.left() + tl.x() && content.top() < outline.top() + tl.y())
                || (!tr.isSquare() && contentRight > right - tr.x() && content.top() < outline.top() + tr.y())
                || (!br.isSquare() && contentRight > right - br.x() && contentBottom > bottom - br.y())
                || (!bl.isSquare() && content.left() < outline.left() + bl.x() && contentBottom > bottom - bl.y());
    }
}
