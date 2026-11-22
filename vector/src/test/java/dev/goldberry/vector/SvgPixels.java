package dev.goldberry.vector;

import static org.junit.jupiter.api.Assertions.assertEquals;

import dev.goldberry.image.Image;

/// Asserting on what a document drew: a pixel's colour, or its being empty.
final class SvgPixels {

    /// How far a channel may be from what is asked: an edge pixel of a shape is
    /// antialiased, and a colour through premultiplication rounds.
    static final int CHANNEL = 2;

    private SvgPixels() {}

    /// Asserts the pixel at (`x`, `y`) is `argb`, unpremultiplied, each channel
    /// within [#CHANNEL].
    static void assertPixel(Image image, int x, int y, int argb) {
        var actual = image.argb(x, y);
        for (var shift = 0; shift < 32; shift += 8) {
            var want = (argb >>> shift) & 0xFF;
            var got = (actual >>> shift) & 0xFF;
            if (Math.abs(want - got) > CHANNEL) {
                assertEquals(
                        hex(argb),
                        hex(actual),
                        "pixel (" + x + ", " + y + ") of a " + image.width() + "x" + image.height() + " raster");
            }
        }
    }

    /// Asserts nothing was drawn at (`x`, `y`).
    static void assertEmpty(Image image, int x, int y) {
        assertEquals(0, image.argb(x, y) >>> 24, "alpha at (" + x + ", " + y + "): " + hex(image.argb(x, y)));
    }

    /// Asserts nothing was drawn anywhere.
    static void assertAllEmpty(Image image) {
        for (var y = 0; y < image.height(); y++) {
            for (var x = 0; x < image.width(); x++) {
                assertEmpty(image, x, y);
            }
        }
    }

    static String hex(int argb) {
        return String.format("%08X", argb);
    }
}
