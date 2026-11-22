package dev.goldberry.vector.view;

import java.nio.ByteBuffer;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

import dev.goldberry.image.Image;
import dev.goldberry.image.ImageDecodeException;
import dev.goldberry.render.model.LogicalSize;
import dev.goldberry.vector.Svg;

/// A document an [SvgView] has read and checked: its bytes, its key, its
/// natural size, and its drawings in each colour asked for.
///
/// Read once, off the UI thread, and parsed there once in black to learn its
/// size and that it is a document at all. A drawing in another `currentColor`
/// is the bytes parsed again with it, which for an icon is a fraction of a
/// millisecond, and remembered: a theme has a handful of colours a view is
/// drawn in.
final class SvgDocument {

    /// How many drawings, of every document and colour, are remembered.
    static final int REMEMBERED = 128;

    /// One document in one colour.
    private record Key(String document, int argb) {}

    /// Every view's drawings, least recently used first. An [Image] of a
    /// document is its natural raster and the parsed document, released by its
    /// cleaner once dropped from here and from every frame that drew it.
    private static final Map<Key, Image> DRAWINGS = new LinkedHashMap<>(16, 0.75f, true) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<Key, Image> eldest) {
            return size() > REMEMBERED;
        }
    };

    private final byte[] bytes;
    private final String key;
    private final LogicalSize naturalSize;

    private SvgDocument(byte[] bytes, String key, LogicalSize naturalSize) {
        this.bytes = bytes;
        this.key = key;
        this.naturalSize = naturalSize;
    }

    /// Reads `source` and checks it is a document, blocking.
    ///
    /// @throws java.io.UncheckedIOException when it cannot be read
    /// @throws ImageDecodeException          when it is not a document
    /// @throws IllegalStateException         when `libgoldberry-vector` is not
    ///                                       available here
    static SvgDocument read(SvgSource source) {
        var bytes = source.read();
        var key = source.key();
        var black = draw(key, bytes, Svg.DEFAULT_CURRENT_COLOR);
        var natural = black.scalable().orElseThrow().naturalSize();
        return new SvgDocument(bytes, key, natural);
    }

    /// The size the document gives itself, in logical pixels.
    LogicalSize naturalSize() {
        return naturalSize;
    }

    /// What the parsed drawings are remembered by.
    String key() {
        return key;
    }

    /// The document with `currentColor` the colour `argb`: its natural raster,
    /// carrying the parsed document, so a frame draws it at the pixels it covers.
    Image in(int argb) {
        return draw(key, bytes, argb);
    }

    private static Image draw(String key, byte[] bytes, int argb) {
        var remembered = new Key(key, argb);
        synchronized (DRAWINGS) {
            var drawn = DRAWINGS.get(remembered);
            if (drawn != null) {
                return drawn;
            }
        }
        var drawn = Image.of(Svg.parse(ByteBuffer.wrap(bytes), argb));
        synchronized (DRAWINGS) {
            var raced = DRAWINGS.putIfAbsent(remembered, drawn);
            return Objects.requireNonNullElse(raced, drawn);
        }
    }

    /// Forgets every drawing, for a test.
    static void forget() {
        synchronized (DRAWINGS) {
            DRAWINGS.clear();
        }
    }

    /// How many drawings are remembered, for a test.
    static int remembered() {
        synchronized (DRAWINGS) {
            return DRAWINGS.size();
        }
    }
}
