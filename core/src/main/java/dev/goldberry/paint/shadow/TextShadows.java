package dev.goldberry.paint.shadow;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

import org.jspecify.annotations.Nullable;

import dev.goldberry.css.value.Shadow;
import dev.goldberry.image.Image;
import dev.goldberry.paint.Box;
import dev.goldberry.paint.Frame;
import dev.goldberry.render.PixelBuffer;
import dev.goldberry.render.model.PhysicalSize;
import dev.goldberry.render.model.PixelFormat;
import dev.goldberry.text.Paragraph;
import dev.goldberry.text.flow.TextFlow;

/// `text-shadow`: the glyphs drawn again under themselves, offset, in the
/// shadow's colour, and blurred.
///
/// ```java
/// TextShadows.paint(frame, box.text(), x, y, width); // before the text itself
/// ```
///
/// A shadow with no blur is the paragraph painted once more at its offset,
/// which costs what the text costs. A blurred one is the paragraph's coverage
/// rasterized into a buffer of its own, blurred by [CoverageBlur] at a
/// standard deviation of half the blur radius, as CSS says, coloured, and
/// drawn as a picture. That raster is kept, keyed by the paragraph, its width,
/// its flow, the shadow and the display scale, so a still label costs one
/// blur and a repaint costs a blit.
///
/// Read more: [Styling](https://goldberry.dev/docs/guide/styling.html#typography).
public final class TextShadows {

    /// How many blurred rasters are kept, least recently used first out. A
    /// window has a handful of shadowed labels, not hundreds.
    private static final int KEPT = 64;

    /// The longest side of a blurred raster, in pixels. A paragraph larger
    /// than this draws its shadow unblurred rather than allocating a buffer the
    /// size of a page.
    private static final int MAX_SIDE = 4096;

    private static final Map<Key, Image> RASTERS = new LinkedHashMap<>(KEPT, 0.75f, true) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<Key, Image> eldest) {
            return size() > KEPT;
        }
    };

    private TextShadows() {}

    /// How far past the glyphs a shadow's blur reaches: three standard
    /// deviations, past which a Gaussian puts nothing a pixel can show.
    public static double extent(Shadow shadow) {
        return Math.ceil(1.5 * shadow.blur());
    }

    /// Draws `text`'s shadows under where its glyphs go, the last of the list
    /// first, as CSS stacks them.
    ///
    /// @param x     the left of the text, inside the box's padding
    /// @param y     the top of the text
    /// @param width the width the text is laid out at
    public static void paint(Frame frame, Box.Text text, double x, double y, double width) {
        Objects.requireNonNull(frame, "frame");
        Objects.requireNonNull(text, "text");
        var shadows = text.shadows();
        for (var i = shadows.size() - 1; i >= 0; i--) {
            var shadow = shadows.get(i);
            if (!shadow.hasInk()) {
                continue;
            }
            var raster = shadow.blur() > 0 ? raster(frame, text.paragraph(), text.flow(), width, shadow) : null;
            if (raster == null) {
                text.paragraph()
                        .paint(frame, x + shadow.offsetX(), y + shadow.offsetY(), width, shadow.argb(), text.flow());
                continue;
            }
            var factor = frame.scale().factor();
            var pad = pad(text.paragraph(), shadow);
            frame.drawImage(
                    raster,
                    x + shadow.offsetX() - pad,
                    y + shadow.offsetY() - pad,
                    raster.width() / factor,
                    raster.height() / factor);
        }
    }

    /// How far the raster reaches past the text's own box: the blur, and room
    /// for ink a glyph puts outside its advance and its line, an italic's
    /// overhang or a descender below a tight line height.
    private static double pad(Paragraph paragraph, Shadow shadow) {
        return extent(shadow) + Math.ceil(paragraph.font().size() * 0.3);
    }

    /// The blurred shadow of `paragraph` at `width`, or null when it would be
    /// too large to blur.
    private static @Nullable Image raster(
            Frame frame, Paragraph paragraph, TextFlow flow, double width, Shadow shadow) {
        var scale = frame.scale();
        var key = new Key(paragraph, width, flow, shadow, scale.factor());
        synchronized (RASTERS) {
            var kept = RASTERS.get(key);
            if (kept != null) {
                return kept;
            }
        }
        var wrapped = paragraph.layout(width, flow);
        var single = paragraph.layout(Paragraph.UNCONSTRAINED);
        var pad = pad(paragraph, shadow);
        var logicalWidth = Math.max(Math.max(width, wrapped.width()), single.width()) + 2 * pad;
        var logicalHeight = Math.max(wrapped.height(), single.height()) + 2 * pad;
        var factor = scale.factor();
        var pixelWidth = (int) Math.ceil(logicalWidth * factor);
        var pixelHeight = (int) Math.ceil(logicalHeight * factor);
        if (pixelWidth <= 0 || pixelHeight <= 0 || pixelWidth > MAX_SIDE || pixelHeight > MAX_SIDE) {
            return null;
        }
        var buffer = PixelBuffer.allocate(new PhysicalSize(pixelWidth, pixelHeight), PixelFormat.BGRA32_PREMULTIPLIED);
        var coverage = Frame.over(buffer, scale, 0);
        try {
            paragraph.paint(coverage, pad, pad, width, 0xFF000000, flow);
        } finally {
            coverage.end();
        }
        colour(buffer, shadow.argb(), shadow.blur() / 2 * factor);
        var image = Image.of(buffer);
        synchronized (RASTERS) {
            RASTERS.put(key, image);
        }
        return image;
    }

    /// Blurs the coverage `buffer` holds in its alpha and replaces every pixel
    /// with `argb` at that coverage, premultiplied.
    private static void colour(PixelBuffer buffer, int argb, double sigma) {
        var width = buffer.size().width();
        var height = buffer.size().height();
        var stride = buffer.stride();
        var pixels = buffer.pixels();
        var mask = new int[width * height];
        for (var y = 0; y < height; y++) {
            for (var x = 0; x < width; x++) {
                mask[y * width + x] = pixels.get(y * stride + x * 4 + 3) & 0xFF;
            }
        }
        CoverageBlur.blur(mask, width, height, sigma);
        var alpha = (argb >>> 24) & 0xFF;
        var red = (argb >>> 16) & 0xFF;
        var green = (argb >>> 8) & 0xFF;
        var blue = argb & 0xFF;
        for (var y = 0; y < height; y++) {
            for (var x = 0; x < width; x++) {
                var a = mask[y * width + x] * alpha / 255;
                var at = y * stride + x * 4;
                pixels.put(at, (byte) (blue * a / 255));
                pixels.put(at + 1, (byte) (green * a / 255));
                pixels.put(at + 2, (byte) (red * a / 255));
                pixels.put(at + 3, (byte) a);
            }
        }
    }

    /// What a raster is kept by. The paragraph by identity: the renderer's
    /// cache hands out one instance per text, and two equal strings in two
    /// paragraphs are two rasters, which is the safe error.
    private record Key(Paragraph paragraph, double width, TextFlow flow, Shadow shadow, float scale) {

        @Override
        public boolean equals(Object other) {
            return other instanceof Key key
                    && paragraph == key.paragraph
                    && Double.compare(width, key.width) == 0
                    && flow.equals(key.flow)
                    && shadow.equals(key.shadow)
                    && Float.compare(scale, key.scale) == 0;
        }

        @Override
        public int hashCode() {
            return Objects.hash(System.identityHashCode(paragraph), width, flow, shadow, scale);
        }
    }
}
