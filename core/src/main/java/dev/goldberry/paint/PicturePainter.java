package dev.goldberry.paint;

import java.util.HashMap;
import java.util.Map;
import java.util.WeakHashMap;

import org.jspecify.annotations.Nullable;

import dev.goldberry.css.Corners;
import dev.goldberry.css.background.Background;
import dev.goldberry.css.image.CssImage;
import dev.goldberry.css.image.StyleImages;
import dev.goldberry.image.Image;
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

    /// Paints layer `index` of `fill`, which is `url`, over the box at `(x, y)`.
    static void paint(
            Frame frame,
            CssImage.Url url,
            Background fill,
            int index,
            double x,
            double y,
            double width,
            double height,
            Corners corners) {
        var picture = StyleImages.resolve(url, frame.scale().factor());
        if (picture == null || !(width > 0) || !(height > 0) || url.alpha() <= 0) {
            return;
        }
        var size = fill.size(index).resolve(width, height, picture.naturalWidth(), picture.naturalHeight());
        var repeat = fill.repeat(index);
        var tiles = Tiles.of(
                fill.position().x(),
                fill.position().y(),
                size[0],
                size[1],
                width,
                height,
                repeat.across(),
                repeat.down());
        if (tiles == null) {
            return;
        }
        var image = picture.image();
        if (tiles.count() > MAX_TILES) {
            var factor = (int) Math.ceil(Math.sqrt((double) tiles.count() / MAX_TILES));
            var across = repeat.across() ? factor : 1;
            var down = repeat.down() ? factor : 1;
            image = repeated(image, across, down);
            tiles = Tiles.of(
                    fill.position().x(),
                    fill.position().y(),
                    size[0] * across,
                    size[1] * down,
                    width,
                    height,
                    repeat.across(),
                    repeat.down());
            if (tiles == null) {
                return;
            }
        }
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
        var placed = tiles;
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
                placed.draw(inner, drawn, x - left, y - top, scale, 1);
                inner.drawLayer(0, 0, outline, BlendCompOp.DST_IN);
            });
            frame.drawLayer(left, top, layer, url.alpha());
        }
    }

    /// Which tiles of a picture `tileWidth` × `tileHeight`, starting at
    /// `(originX, originY)` in a box's own coordinates, touch a box `width` ×
    /// `height`.
    ///
    /// @param originX     where the origin tile's left edge is
    /// @param originY     where its top edge is
    /// @param tileWidth   how wide a tile is drawn
    /// @param tileHeight  how tall
    /// @param firstColumn the first column, counted from the origin tile's 0
    /// @param firstRow    the first row, the same way
    /// @param columns     how many columns
    /// @param rows        how many rows
    record Tiles(
            double originX,
            double originY,
            double tileWidth,
            double tileHeight,
            int firstColumn,
            int firstRow,
            int columns,
            int rows) {

        /// The tiles, or null when there are none to draw.
        static @Nullable Tiles of(
                double originX,
                double originY,
                double tileWidth,
                double tileHeight,
                double width,
                double height,
                boolean across,
                boolean down) {
            if (!(tileWidth > 0) || !(tileHeight > 0) || !(width > 0) || !(height > 0)) {
                return null;
            }
            var firstColumn = across ? (int) Math.floor(-originX / tileWidth) : 0;
            var lastColumn = across ? (int) Math.ceil((width - originX) / tileWidth) : 1;
            var firstRow = down ? (int) Math.floor(-originY / tileHeight) : 0;
            var lastRow = down ? (int) Math.ceil((height - originY) / tileHeight) : 1;
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
                var top = snap(y + originY + row * tileHeight, scale);
                var bottom = snap(y + originY + (row + 1) * tileHeight, scale);
                if (!(bottom > top)) {
                    continue;
                }
                for (var column = firstColumn; column < firstColumn + columns; column++) {
                    var left = snap(x + originX + column * tileWidth, scale);
                    var right = snap(x + originX + (column + 1) * tileWidth, scale);
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
