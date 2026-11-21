package dev.goldberry.mic.view.draw;

import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.goldberry.image.Image;
import dev.goldberry.offscreen.Offscreen;
import dev.goldberry.paint.Painter;

/// Paints a plot alone on black, and reads its pixels back.
final class Pixels {

    /// What every picture here is painted over.
    static final int BLACK = 0xFF000000;

    private Pixels() {}

    static Image paint(int width, int height, Painter painter) {
        return Offscreen.of(width, height).background(BLACK).paint(painter);
    }

    /// `argb` at `alpha`, over black: what a faint bar should read.
    static int over(int argb, double alpha) {
        var over = 0xFF000000;
        for (var shift = 0; shift <= 16; shift += 8) {
            over |= (int) Math.round(((argb >> shift) & 0xFF) * alpha) << shift;
        }
        return over;
    }

    /// Asserts the pixel at `x`, `y` is `expected` within a step or two of
    /// each channel, which is what rounding a blend leaves.
    static void assertNear(int expected, Image image, int x, int y) {
        assertNear(expected, image, x, y, "");
    }

    /// The same, saying `why` when it fails.
    static void assertNear(int expected, Image image, int x, int y, String why) {
        var actual = image.argb(x, y);
        var close = true;
        for (var shift = 0; shift <= 16; shift += 8) {
            close &= Math.abs(((expected >> shift) & 0xFF) - ((actual >> shift) & 0xFF)) <= 3;
        }
        var near = close;
        assertTrue(
                near,
                () -> why + " at (" + x + ", " + y + "): expected " + Integer.toHexString(expected) + ", found "
                        + Integer.toHexString(actual));
    }
}
