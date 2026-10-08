package dev.goldberry.css.image;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import dev.goldberry.css.Border;
import dev.goldberry.layout.Length;
import dev.goldberry.render.model.PhysicalRect;

/// The arithmetic of a [BorderImage]: which rectangles of the picture go where
/// on a box.
///
/// Kept apart from the drawing so it can be checked by numbers rather than by
/// pixels. CSS's rules, in order:
///
/// 1. The slice lines are read in the picture's pixels, at its density, and
///    clamped to it.
/// 2. The border image area is the box grown by the outsets.
/// 3. Each side's width is read: a number of border widths, a length, a
///    percentage of the area, or `auto` for the slice's own size. When two
///    opposite widths add up to more than the area, every width shrinks by the
///    same factor.
/// 4. The corners go in the corners of the area at those widths. The edges go
///    between them, scaled across their thickness to the width and filling their
///    length as the repeat says. The middle, when it is drawn, is scaled as the
///    top edge is across and as the left edge is down.
///
/// A corner sliced at 48 pixels of a 1x picture and drawn 48 pixels wide is
/// drawn pixel for pixel, as is one sliced at 96 of its `@2x` variant.
///
/// Read more: [Styling](https://goldberry.dev/docs/guide/styling.html#border-outline-and-shadow).
public final class NineSlice {

    private NineSlice() {}

    /// One piece of the nine: a rectangle of the picture, the rectangle of the
    /// box it fills, and the tiles it fills it with.
    ///
    /// The tiles start at `(x + offsetX, y + offsetY)` and repeat every
    /// `tileWidth` across and `tileHeight` down until the rectangle is covered;
    /// what falls outside it is cut away. A stretched piece is one tile the size
    /// of its rectangle.
    ///
    /// @param source     the picture's pixels
    /// @param x          the rectangle's left edge, in the box's own logical
    ///                   coordinates
    /// @param y          its top edge
    /// @param width      its width
    /// @param height     its height
    /// @param tileWidth  how wide one tile is drawn
    /// @param tileHeight how tall
    /// @param offsetX    where the first tile starts, at or before `x`
    /// @param offsetY    where the first tile starts, at or before `y`
    public record Piece(
            PhysicalRect source,
            double x,
            double y,
            double width,
            double height,
            double tileWidth,
            double tileHeight,
            double offsetX,
            double offsetY) {

        public Piece {
            Objects.requireNonNull(source, "source");
        }

        /// Whether the piece is one tile exactly the size of its rectangle.
        public boolean isStretched() {
            return tileWidth == width && tileHeight == height && offsetX == 0 && offsetY == 0;
        }
    }

    /// The pieces to draw for `image`, a picture `imageWidth` × `imageHeight`
    /// pixels at `density`, on a box `width` × `height` whose border is
    /// `border`. Corners first, then the edges, then the middle when it is
    /// drawn; a piece with nothing in it is left out.
    public static List<Piece> pieces(
            BorderImage image,
            int imageWidth,
            int imageHeight,
            int density,
            Border border,
            double width,
            double height) {
        Objects.requireNonNull(image, "image");
        Objects.requireNonNull(border, "border");
        var slice = image.slice();
        // 1. The slice lines, in the picture's pixels.
        var top = line(slice.top(), imageHeight, density);
        var right = line(slice.right(), imageWidth, density);
        var bottom = line(slice.bottom(), imageHeight, density);
        var left = line(slice.left(), imageWidth, density);
        if (top + bottom > imageHeight) {
            top = Math.min(top, imageHeight);
            bottom = imageHeight - top;
        }
        if (left + right > imageWidth) {
            left = Math.min(left, imageWidth);
            right = imageWidth - left;
        }
        // 2. The area.
        var outsets = image.outsetsFor(border);
        var areaX = -outsets[3];
        var areaY = -outsets[0];
        var areaWidth = width + outsets[1] + outsets[3];
        var areaHeight = height + outsets[0] + outsets[2];
        if (!(areaWidth > 0) || !(areaHeight > 0)) {
            return List.of();
        }
        // 3. The widths.
        var widths = image.widths();
        var drawTop = extent(widths.get(0), border.top().width(), areaHeight, top, density);
        var drawRight = extent(widths.get(1), border.right().width(), areaWidth, right, density);
        var drawBottom = extent(widths.get(2), border.bottom().width(), areaHeight, bottom, density);
        var drawLeft = extent(widths.get(3), border.left().width(), areaWidth, left, density);
        var factor = Math.min(1, Math.min(fit(areaWidth, drawLeft + drawRight), fit(areaHeight, drawTop + drawBottom)));
        drawTop *= factor;
        drawRight *= factor;
        drawBottom *= factor;
        drawLeft *= factor;

        // 4. The pieces. Source columns and rows, and the box's.
        int[] sx = {0, left, imageWidth - right, imageWidth};
        int[] sy = {0, top, imageHeight - bottom, imageHeight};
        double[] dx = {areaX, areaX + drawLeft, areaX + areaWidth - drawRight, areaX + areaWidth};
        double[] dy = {areaY, areaY + drawTop, areaY + areaHeight - drawBottom, areaY + areaHeight};

        var pieces = new ArrayList<Piece>(9);
        // Corners: stretched into their rectangles.
        for (var row : new int[] {0, 2}) {
            for (var column : new int[] {0, 2}) {
                add(pieces, sx, sy, dx, dy, column, row, Scale.STRETCH, Scale.STRETCH, 0, 0, image);
            }
        }
        var middleWidth = sx[2] - sx[1];
        var middleHeight = sy[2] - sy[1];
        // How a horizontal edge's tile is sized across: its height scaled to the
        // width it is drawn at, and its length by the same factor.
        var topScale = scale(drawTop, top, drawBottom, bottom);
        var leftScale = scale(drawLeft, left, drawRight, right);
        // Edges: top and bottom tile across, left and right tile down.
        add(pieces, sx, sy, dx, dy, 1, 0, Scale.TILE, Scale.STRETCH, ratio(drawTop, top), 0, image);
        add(pieces, sx, sy, dx, dy, 1, 2, Scale.TILE, Scale.STRETCH, ratio(drawBottom, bottom), 0, image);
        add(pieces, sx, sy, dx, dy, 0, 1, Scale.STRETCH, Scale.TILE, 0, ratio(drawLeft, left), image);
        add(pieces, sx, sy, dx, dy, 2, 1, Scale.STRETCH, Scale.TILE, 0, ratio(drawRight, right), image);
        if (slice.fill() && middleWidth > 0 && middleHeight > 0) {
            add(pieces, sx, sy, dx, dy, 1, 1, Scale.TILE, Scale.TILE, topScale, leftScale, image);
        }
        return List.copyOf(pieces);
    }

    /// Whether a piece is stretched into its rectangle or tiled along it.
    private enum Scale {
        STRETCH,
        TILE
    }

    /// Adds the piece at `column`, `row` of the three-by-three grid.
    ///
    /// @param across the factor from the picture's pixels to the box's across a
    ///               tiled piece, which keeps its shape
    /// @param down   the same, down
    private static void add(
            List<Piece> into,
            int[] sx,
            int[] sy,
            double[] dx,
            double[] dy,
            int column,
            int row,
            Scale horizontal,
            Scale vertical,
            double across,
            double down,
            BorderImage image) {
        var source = new PhysicalRect(sx[column], sy[row], sx[column + 1] - sx[column], sy[row + 1] - sy[row]);
        var x = dx[column];
        var y = dy[row];
        var width = dx[column + 1] - x;
        var height = dy[row + 1] - y;
        if (source.isEmpty() || !(width > 0) || !(height > 0)) {
            return;
        }
        // A tiled axis whose factor is zero or unknown is drawn unscaled, which
        // is what CSS says for a middle with no edge to borrow a scale from.
        var tileWidth = horizontal == Scale.STRETCH ? width : source.width() * (across > 0 ? across : 1);
        var tileHeight = vertical == Scale.STRETCH ? height : source.height() * (down > 0 ? down : 1);
        var acrossTiles =
                horizontal == Scale.STRETCH ? new double[] {width, 0} : tile(image.across(), width, tileWidth);
        var downTiles = vertical == Scale.STRETCH ? new double[] {height, 0} : tile(image.down(), height, tileHeight);
        into.add(new Piece(source, x, y, width, height, acrossTiles[0], downTiles[0], acrossTiles[1], downTiles[1]));
    }

    /// One axis's tile size and the offset of the first tile, for a length
    /// filled by tiles `tile` long.
    private static double[] tile(BorderImage.Repeat repeat, double length, double tile) {
        return switch (repeat) {
            case STRETCH -> new double[] {length, 0};
            case ROUND -> {
                var count = Math.max(1, Math.round(length / tile));
                yield new double[] {length / count, 0};
            }
            case REPEAT -> {
                // Centred: one tile's middle on the length's middle, and the
                // first tile starting at or before the start.
                var offset = (length - tile) / 2;
                offset -= Math.ceil(offset / tile) * tile;
                // Plus zero, so a centred tile that starts exactly at the start is 0
                // and not negative zero.
                yield new double[] {tile, offset + 0.0};
            }
        };
    }

    /// A slice line in the picture's pixels: a number at the picture's density,
    /// a percentage of `size`, clamped to it.
    private static int line(Length length, int size, int density) {
        var pixels =
                switch (length) {
                    case Length.Points points -> points.value() * density;
                    case Length.Percent percent -> percent.value() / 100 * size;
                    default -> 0;
                };
        return Math.max(0, Math.min(size, Math.round(pixels)));
    }

    /// One side's drawn width in logical pixels.
    private static double extent(
            BorderImage.Extent extent, double borderWidth, double area, int slicePixels, int density) {
        return switch (extent.kind()) {
            case NUMBER -> extent.value() * borderWidth;
            case LENGTH -> extent.value();
            case PERCENT -> extent.value() / 100 * area;
            case AUTO -> (double) slicePixels / density;
        };
    }

    private static double fit(double area, double sum) {
        return sum > 0 ? area / sum : 1;
    }

    /// Logical pixels per picture pixel, for a piece `slicePixels` thick drawn
    /// `drawn` thick; zero when either is nothing.
    private static double ratio(double drawn, int slicePixels) {
        return slicePixels > 0 && drawn > 0 ? drawn / slicePixels : 0;
    }

    /// The middle's factor on one axis: the first edge's, or the second's when
    /// the first has none.
    private static double scale(double drawn, int slice, double otherDrawn, int otherSlice) {
        var first = ratio(drawn, slice);
        return first > 0 ? first : ratio(otherDrawn, otherSlice);
    }
}
