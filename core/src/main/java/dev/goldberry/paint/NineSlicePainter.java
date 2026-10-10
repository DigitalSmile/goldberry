package dev.goldberry.paint;

import java.util.List;

import dev.goldberry.css.Decoration;
import dev.goldberry.css.image.NineSlice;
import dev.goldberry.css.image.StyleImages;
import dev.goldberry.image.Image;

/// Draws a picture cut in nine: a box's `border-image`, or a painter's
/// [dev.goldberry.paint.slice.NinePatch], as the nine pieces [NineSlice]
/// places, each stretched into its rectangle or tiled along it.
///
/// Every edge is placed on a whole device pixel, so a corner and the edge
/// beside it share their seam exactly and nothing of the box behind shows
/// between them. A tiled piece is clipped to its rectangle; the rasterizer
/// clips to rectangles, which is all a piece is.
final class NineSlicePainter {

    /// The most tiles one piece is drawn with. A slice of a pixel or two, tiled
    /// along a long edge, would otherwise be thousands of blits a frame; past
    /// this the piece is stretched instead.
    static final int MAX_TILES = 4096;

    private NineSlicePainter() {}

    /// Draws `decoration`'s border image over the box at `(x, y)`.
    ///
    /// @return false when there is nothing to draw yet — no picture, or one still
    ///         loading or failed — and the border's colours should be drawn
    ///         instead, which is CSS's rule for a border image that cannot be
    ///         shown
    static boolean paint(Frame frame, Decoration decoration, double x, double y, double width, double height) {
        var borderImage = decoration.borderImage();
        var source = borderImage.source();
        if (source == null) {
            return false;
        }
        var scale = frame.scale().factor();
        var picture = StyleImages.resolve(source, scale);
        if (picture == null) {
            return false;
        }
        var image = picture.image();
        var alpha = source.alpha();
        if (alpha <= 0) {
            return true;
        }
        var pieces = NineSlice.pieces(
                borderImage, image.width(), image.height(), picture.density(), decoration.border(), width, height);
        draw(frame, image, pieces, x, y, alpha);
        return true;
    }

    /// Draws `pieces` of `image` over the rectangle at `(x, y)`, faded to
    /// `alpha`: what a box's border image and a painter's
    /// [dev.goldberry.paint.slice.NinePatch] both come down to.
    static void draw(Frame frame, Image image, List<NineSlice.Piece> pieces, double x, double y, double alpha) {
        var scale = frame.scale().factor();
        for (var piece : pieces) {
            var left = snap(x + piece.x(), scale);
            var top = snap(y + piece.y(), scale);
            var right = snap(x + piece.x() + piece.width(), scale);
            var bottom = snap(y + piece.y() + piece.height(), scale);
            if (!(right > left) || !(bottom > top)) {
                continue;
            }
            var columns = Math.ceil((piece.width() - piece.offsetX()) / piece.tileWidth());
            var rows = Math.ceil((piece.height() - piece.offsetY()) / piece.tileHeight());
            if (piece.isStretched() || columns * rows > MAX_TILES) {
                frame.drawImage(image, piece.source(), left, top, right - left, bottom - top, alpha);
                continue;
            }
            frame.save();
            try {
                frame.clipTo(left, top, right - left, bottom - top);
                tile(frame, image, piece, x, y, scale, alpha);
            } finally {
                frame.restore();
            }
        }
    }

    private static void tile(
            Frame frame, Image image, NineSlice.Piece piece, double x, double y, double scale, double alpha) {
        for (var down = piece.offsetY(); down < piece.height(); down += piece.tileHeight()) {
            var top = snap(y + piece.y() + down, scale);
            var bottom = snap(y + piece.y() + down + piece.tileHeight(), scale);
            if (!(bottom > top)) {
                continue;
            }
            for (var across = piece.offsetX(); across < piece.width(); across += piece.tileWidth()) {
                var left = snap(x + piece.x() + across, scale);
                var right = snap(x + piece.x() + across + piece.tileWidth(), scale);
                if (right > left) {
                    frame.drawImage(image, piece.source(), left, top, right - left, bottom - top, alpha);
                }
            }
        }
    }

    private static double snap(double value, double scale) {
        return Math.round(value * scale) / scale;
    }
}
