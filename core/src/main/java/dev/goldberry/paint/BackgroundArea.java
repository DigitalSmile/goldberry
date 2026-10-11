package dev.goldberry.paint;

import dev.goldberry.css.Corners;
import dev.goldberry.css.background.BackgroundBox;

/// One of a box's three rectangles as a background layer sees it: where it is
/// on the frame, how large, and the corners its outline has there.
///
/// A border takes no layout room here, so the padding box is the box less its
/// border's widths and the content box the box less its padding, where its
/// text starts. Each inner box's corners are the outer ones less the sides,
/// as a border's inner edge is.
///
/// @param x       the left edge, in the frame's logical pixels
/// @param y       the top edge
/// @param width   never negative
/// @param height  never negative
/// @param corners the outline's corners, resolved to pixels
record BackgroundArea(double x, double y, double width, double height, Corners corners) {

    /// `which` of `box`'s rectangles, the box being at `(x, y)`, `width` by
    /// `height`.
    static BackgroundArea of(Box box, BackgroundBox which, double x, double y, double width, double height) {
        var corners = box.decoration().corners().resolvedOn(width, height);
        return switch (which) {
            case BORDER_BOX -> new BackgroundArea(x, y, width, height, corners);
            case PADDING_BOX -> {
                var border = box.decoration().border();
                yield inset(
                        x,
                        y,
                        width,
                        height,
                        corners,
                        border.top().width(),
                        border.right().width(),
                        border.bottom().width(),
                        border.left().width());
            }
            case CONTENT_BOX -> {
                var content = BoxEdges.content(box, width, height);
                yield inset(
                        x, y, width, height, corners, content.top(), content.right(), content.bottom(), content.left());
            }
        };
    }

    /// Whether there is anything inside it.
    boolean isEmpty() {
        return !(width > 0) || !(height > 0);
    }

    private static BackgroundArea inset(
            double x,
            double y,
            double width,
            double height,
            Corners corners,
            double top,
            double right,
            double bottom,
            double left) {
        if (top == 0 && right == 0 && bottom == 0 && left == 0) {
            return new BackgroundArea(x, y, width, height, corners);
        }
        return new BackgroundArea(
                x + left,
                y + top,
                Math.max(0, width - left - right),
                Math.max(0, height - top - bottom),
                corners.inset(top, right, bottom, left));
    }
}
