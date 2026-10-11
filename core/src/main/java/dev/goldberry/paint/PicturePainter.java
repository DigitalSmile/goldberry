package dev.goldberry.paint;

import java.util.HashMap;
import java.util.Map;
import java.util.WeakHashMap;

import org.jspecify.annotations.Nullable;

import dev.goldberry.css.background.Background;
import dev.goldberry.css.background.BackgroundPosition;
import dev.goldberry.css.background.BackgroundRepeat;
import dev.goldberry.css.background.BackgroundSize;
import dev.goldberry.css.image.CssImage;
import dev.goldberry.css.image.StyleImages;
import dev.goldberry.image.Image;
import dev.goldberry.layout.Length;
import dev.goldberry.natives.blend2d.enums.BlendCompOp;
import dev.goldberry.render.PixelBuffer;
import dev.goldberry.render.model.PhysicalSize;
import dev.goldberry.render.model.PixelFormat;

/// Draws a `url()` layer of a box's background: the picture at its
/// `background-size`, tiled as its `background-repeat` says, and clipped to the
/// box's outline.
///
/// ## The clip
///
/// A square box clips to its rectangle, which the rasterizer does for free. A
/// rounded one cannot: the rasterizer clips to rectangles and nothing else, and
/// a picture has no shape of its own to fill the outline with the way a
/// gradient does. So the tiles are drawn into a layer the size of the box, and
/// a second layer holding the outline keeps only what is inside it: two
/// allocations for a rounded box with a picture, and none for anything else.
///
/// ## Tiles
///
/// Each tile is placed on whole device pixels, so two neighbours share an edge
/// exactly and no seam of the colour underneath shows between them. A picture
/// so small that the box would take thousands of tiles is first repeated into a
/// larger one, kept beside the picture, so a frame is never thousands of blits.
final class PicturePainter {

    /// The most tiles one layer is drawn with before its picture is repeated
    /// into a larger one.
    static final int MAX_TILES = 1024;

    /// Pictures repeated into larger tiles, by picture and by how many times
    /// across and down. Weak, so a picture nobody draws any more takes its tiles
    /// with it.
    private static final Map<Image, Map<Long, Image>> REPEATED = new WeakHashMap<>();

    private PicturePainter() {}

    /// Paints layer `index` of `fill`, which is `url`: placed and sized in
    /// `origin`, tiled across `clip` and cut to its outline.
    static void paint(
            Frame frame, CssImage.Url url, Background fill, int index, BackgroundArea origin, BackgroundArea clip) {
        var picture = StyleImages.resolve(url, frame.scale().factor());
        if (picture == null || origin.isEmpty() || clip.isEmpty() || url.alpha() <= 0) {
            return;
        }
        var size = fill.size(index)
                .resolve(origin.width(), origin.height(), picture.naturalWidth(), picture.naturalHeight());
        var repeat = fill.repeat(index);
        var placed = Placement.of(fill.size(index), repeat, fill.position(index), origin, size[0], size[1]);
        // The tiles are counted from the clip's corner, which is where they
        // are drawn from.
        var tiles = Tiles.of(
                placed.x() + origin.x() - clip.x(),
                placed.y() + origin.y() - clip.y(),
                placed.width(),
                placed.height(),
                placed.stepX(),
                placed.stepY(),
                clip.width(),
                clip.height(),
                placed.across(),
                placed.down());
        if (tiles == null) {
            return;
        }
        var image = picture.image();
        var spaced = placed.stepX() != placed.width() || placed.stepY() != placed.height();
        if (tiles.count() > MAX_TILES && !spaced) {
            var factor = (int) Math.ceil(Math.sqrt((double) tiles.count() / MAX_TILES));
            var across = placed.across() ? factor : 1;
            var down = placed.down() ? factor : 1;
            image = repeated(image, across, down);
            tiles = Tiles.of(
                    placed.x() + origin.x() - clip.x(),
                    placed.y() + origin.y() - clip.y(),
                    placed.width() * across,
                    placed.height() * down,
                    placed.width() * across,
                    placed.height() * down,
                    clip.width(),
                    clip.height(),
                    placed.across(),
                    placed.down());
            if (tiles == null) {
                return;
            }
        }
        var x = clip.x();
        var y = clip.y();
        var width = clip.width();
        var height = clip.height();
        var corners = clip.corners();
        var scale = frame.scale().factor();
        if (corners.isSquare()) {
            frame.save();
            try {
                frame.clipTo(x, y, width, height);
                tiles.draw(frame, image, x, y, scale, url.alpha());
            } finally {
                frame.restore();
            }
            return;
        }
        // The layer covers the box's device pixels, so it lands on the frame
        // pixel for pixel rather than resampled by a fraction.
        var left = Math.floor(x * scale) / scale;
        var top = Math.floor(y * scale) / scale;
        var pixels = new PhysicalSize(
                (int) Math.ceil((x + width) * scale) - (int) Math.floor(x * scale),
                (int) Math.ceil((y + height) * scale) - (int) Math.floor(y * scale));
        if (pixels.isEmpty()) {
            return;
        }
        var drawn = image;
        var laid = tiles;
        try (var outline = Layer.of(pixels);
                var layer = Layer.of(pixels)) {
            // The outline as a mask, and the tiles kept only where it is: a
            // blit's operator is honoured where a fill's is not, so the cut is a
            // destination-in blit of the outline over the tiles.
            outline.paint(frame.scale(), mask -> {
                var shape = mask.borrowPath();
                try {
                    RoundRect.addTo(shape, x - left, y - top, width, height, corners);
                    mask.fillPath(0, 0, shape, 0xFF000000);
                } finally {
                    mask.releasePath();
                }
            });
            layer.paint(frame.scale(), inner -> {
                laid.draw(inner, drawn, x - left, y - top, scale, 1);
                inner.drawLayer(0, 0, outline, BlendCompOp.DST_IN);
            });
            frame.drawLayer(left, top, layer, url.alpha());
        }
    }

    /// Where one layer's tiles go in its origin's rectangle: the first tile's
    /// corner, a tile's size, and how far apart two tiles are on each axis,
    /// which is the tile's size unless `space` spreads them.
    ///
    /// @param x      the first tile's left edge, from the origin's
    /// @param y      its top edge
    /// @param across whether the picture is tiled across
    /// @param down   whether it is tiled down
    record Placement(
            double x, double y, double width, double height, double stepX, double stepY, boolean across, boolean down) {

        /// The placement of a picture drawn `width` by `height` under `size`,
        /// tiled as `repeat` says from `position` in `origin`.
        static Placement of(
                BackgroundSize size,
                BackgroundRepeat repeat,
                BackgroundPosition position,
                BackgroundArea origin,
                double width,
                double height) {
            var w = width;
            var h = height;
            // `round` stretches a tile until a whole number fit; an axis whose
            // size was left to the picture's shape keeps that shape.
            var roundX = repeat.x() == BackgroundRepeat.Tiling.ROUND && w > 0;
            var roundY = repeat.y() == BackgroundRepeat.Tiling.ROUND && h > 0;
            var autoWidth = size.kind() == BackgroundSize.Kind.LENGTHS && size.width() == Length.AUTO;
            var autoHeight = size.kind() == BackgroundSize.Kind.LENGTHS && size.height() == Length.AUTO;
            if (roundX) {
                var rounded = origin.width() / Math.max(1, Math.round(origin.width() / w));
                if (!roundY && autoHeight) {
                    h *= rounded / w;
                }
                w = rounded;
            }
            if (roundY) {
                var rounded = origin.height() / Math.max(1, Math.round(origin.height() / h));
                if (!roundX && autoWidth) {
                    w *= rounded / h;
                }
                h = rounded;
            }
            var offset = position.offset(origin.width(), origin.height(), w, h);
            var x = Axis.of(repeat.x(), offset[0], w, origin.width());
            var y = Axis.of(repeat.y(), offset[1], h, origin.height());
            return new Placement(x.start(), y.start(), w, h, x.step(), y.step(), x.tiled(), y.tiled());
        }
    }

    /// One axis of a placement: where the first tile starts, the distance
    /// between two, and whether there is more than one.
    private record Axis(double start, double step, boolean tiled) {

        static Axis of(BackgroundRepeat.Tiling tiling, double offset, double tile, double room) {
            if (tiling == BackgroundRepeat.Tiling.SPACE) {
                var count = tile > 0 ? Math.floor(room / tile) : 0;
                // Two or more fit: the first and last touch the edges and the
                // rest share the room left over. One or none: placed once.
                if (count >= 2) {
                    return new Axis(0, tile + (room - count * tile) / (count - 1), true);
                }
                return new Axis(offset, tile, false);
            }
            return new Axis(offset, tile, tiling != BackgroundRepeat.Tiling.NO_REPEAT);
        }
    }

    /// Which tiles of a picture `tileWidth` × `tileHeight`, one every `stepX`
    /// across and `stepY` down from `(originX, originY)` in a box's own
    /// coordinates, touch a box `width` × `height`.
    ///
    /// @param originX     where the origin tile's left edge is
    /// @param originY     where its top edge is
    /// @param tileWidth   how wide a tile is drawn
    /// @param tileHeight  how tall
    /// @param stepX       from one tile's left edge to the next's
    /// @param stepY       from one tile's top edge to the next's
    /// @param firstColumn the first column, counted from the origin tile's 0
    /// @param firstRow    the first row, the same way
    /// @param columns     how many columns
    /// @param rows        how many rows
    record Tiles(
            double originX,
            double originY,
            double tileWidth,
            double tileHeight,
            double stepX,
            double stepY,
            int firstColumn,
            int firstRow,
            int columns,
            int rows) {

        /// The tiles one every `stepX` and `stepY`, or null when there are none
        /// to draw.
        static @Nullable Tiles of(
                double originX,
                double originY,
                double tileWidth,
                double tileHeight,
                double stepX,
                double stepY,
                double width,
                double height,
                boolean across,
                boolean down) {
            if (!(tileWidth > 0) || !(tileHeight > 0) || !(width > 0) || !(height > 0)) {
                return null;
            }
            // A tile reaches `tileWidth` past where it starts, so the first one
            // that touches the box may start before it by that much.
            var firstColumn = across ? (int) Math.floor((-originX - tileWidth) / stepX) + 1 : 0;
            var lastColumn = across ? (int) Math.ceil((width - originX) / stepX) : 1;
            var firstRow = down ? (int) Math.floor((-originY - tileHeight) / stepY) + 1 : 0;
            var lastRow = down ? (int) Math.ceil((height - originY) / stepY) : 1;
            if (lastColumn <= firstColumn || lastRow <= firstRow) {
                return null;
            }
            // A picture drawn once that misses the box entirely is no tile at all.
            if (!across && (originX >= width || originX + tileWidth <= 0)) {
                return null;
            }
            if (!down && (originY >= height || originY + tileHeight <= 0)) {
                return null;
            }
            return new Tiles(
                    originX,
                    originY,
                    tileWidth,
                    tileHeight,
                    stepX,
                    stepY,
                    firstColumn,
                    firstRow,
                    lastColumn - firstColumn,
                    lastRow - firstRow);
        }

        long count() {
            return (long) columns * rows;
        }

        /// Draws every tile of `image` with the box's corner at `(x, y)`, each
        /// edge on a whole device pixel at `scale`.
        void draw(Frame frame, Image image, double x, double y, double scale, double alpha) {
            for (var row = firstRow; row < firstRow + rows; row++) {
                var top = snap(y + originY + row * stepY, scale);
                var bottom = snap(y + originY + row * stepY + tileHeight, scale);
                if (!(bottom > top)) {
                    continue;
                }
                for (var column = firstColumn; column < firstColumn + columns; column++) {
                    var left = snap(x + originX + column * stepX, scale);
                    var right = snap(x + originX + column * stepX + tileWidth, scale);
                    if (right > left) {
                        frame.drawImage(image, left, top, right - left, bottom - top, alpha);
                    }
                }
            }
        }

        private static double snap(double value, double scale) {
            return Math.round(value * scale) / scale;
        }
    }

    /// `image` repeated `across` times across and `down` times down, as one
    /// picture: a larger tile with the same pattern.
    static Image repeated(Image image, int across, int down) {
        if (across == 1 && down == 1) {
            return image;
        }
        var key = (long) across << 32 | down;
        synchronized (REPEATED) {
            var made = REPEATED.computeIfAbsent(image, _ -> new HashMap<>());
            var existing = made.get(key);
            if (existing != null) {
                return existing;
            }
            var width = image.width();
            var height = image.height();
            var buffer = PixelBuffer.allocate(
                    new PhysicalSize(Math.multiplyExact(width, across), Math.multiplyExact(height, down)),
                    PixelFormat.BGRA32_PREMULTIPLIED);
            var from = image.pixels();
            var to = buffer.pixels();
            var row = width * 4;
            for (var y = 0; y < height * down; y++) {
                for (var x = 0; x < across; x++) {
                    to.put(y * buffer.stride() + x * row, from.pixels(), (y % height) * from.stride(), row);
                }
            }
            var result = Image.of(buffer);
            made.put(key, result);
            return result;
        }
    }
}
