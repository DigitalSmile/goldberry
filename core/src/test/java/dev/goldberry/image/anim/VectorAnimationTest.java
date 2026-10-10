package dev.goldberry.image.anim;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.ByteBuffer;
import java.util.Objects;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import dev.goldberry.golden.GoldenImage;
import dev.goldberry.image.Image;
import dev.goldberry.image.ImageDecodeException;

/// A Lottie document drawn: where its time goes, and what its pictures look
/// like at a few moments and two sizes.
///
/// The fixtures are hand-written and small, one feature each, under
/// `dev/goldberry/image/lottie` in the test resources; `sticker.json` is all of
/// them at once and is the golden.
class VectorAnimationTest {

    private static final int TRANSPARENT = 0;

    static VectorAnimation load(String name) {
        try (var in = VectorAnimationTest.class.getResourceAsStream("/dev/goldberry/image/lottie/" + name)) {
            return VectorAnimation.of(Objects.requireNonNull(in, name).readAllBytes());
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /// The pixel at `(x, y)` of a 100-unit canvas drawn at `size`, read where the
    /// same point lands.
    private static int at(Image image, double x, double y) {
        var scale = image.width() / 100.0;
        return image.argb((int) Math.floor(x * scale), (int) Math.floor(y * scale));
    }

    private static void assertColour(int expected, int actual, String what) {
        for (var shift = 0; shift <= 24; shift += 8) {
            var e = (expected >>> shift) & 0xFF;
            var a = (actual >>> shift) & 0xFF;
            assertTrue(
                    Math.abs(e - a) <= 3,
                    () -> what + ": expected " + Integer.toHexString(expected) + ", was "
                            + Integer.toHexString(actual));
        }
    }

    private static int alpha(int argb) {
        return argb >>> 24;
    }

    @Nested
    @DisplayName("time")
    class Time {

        @Test
        @DisplayName("one pass is op minus ip frames at fr frames a second")
        void duration() {
            var rect = load("static-rect.json");
            assertEquals(1000, rect.durationMillis());
            assertEquals(30, rect.frameRate());
            assertEquals(3000, load("sticker.json").durationMillis(), "180 frames at 60 fps");
        }

        @Test
        @DisplayName("endless until it is told how often to play, as an Animation is")
        void endless() {
            var rect = load("static-rect.json");
            assertTrue(rect.isEndless());
            assertEquals(-1, rect.totalMillis());
            assertFalse(rect.isDoneAt(Long.MAX_VALUE));

            var twice = rect.loops(2);
            assertFalse(twice.isEndless());
            assertEquals(2000, twice.totalMillis());
            assertFalse(twice.isDoneAt(1999));
            assertTrue(twice.isDoneAt(2000));
            assertThrows(IllegalArgumentException.class, () -> rect.loops(-1));
        }

        @Test
        @DisplayName("a frame number within the pass, and the last frame once it is done")
        void frames() {
            var rect = load("static-rect.json");
            assertEquals(0, rect.frameAt(0), 1e-9);
            assertEquals(15, rect.frameAt(500), 1e-9);
            assertEquals(15, rect.frameAt(1500), 1e-9, "the second pass");
            assertEquals(0, rect.frameAt(-20), 1e-9, "a negative time reads as the start");
            assertEquals(29, rect.loops(1).frameAt(5000), 1e-9, "a finished one holds its last frame");
        }
    }

    @Nested
    @DisplayName("pictures")
    class Pictures {

        @Test
        @DisplayName("a still rectangle, at two sizes")
        void staticRect() {
            var rect = load("static-rect.json");
            for (var size : new int[] {100, 200}) {
                var image = rect.imageAt(0, size, size);
                assertEquals(size, image.width());
                assertColour(0xFFFF0000, at(image, 50, 50), "inside at " + size);
                assertColour(0xFFFF0000, at(image, 21, 31), "just inside the corner at " + size);
                assertEquals(TRANSPARENT, at(image, 10, 10), "outside at " + size);
                assertEquals(TRANSPARENT, at(image, 50, 75), "below at " + size);
            }
        }

        @Test
        @DisplayName("a tgs draws what its JSON draws")
        void tgs() {
            var plain = load("static-rect.json").imageAt(0, 64, 64);
            var packed = load("static-rect.tgs").imageAt(0, 64, 64);
            for (var y = 0; y < 64; y += 3) {
                for (var x = 0; x < 64; x += 3) {
                    assertEquals(plain.argb(x, y), packed.argb(x, y));
                }
            }
        }

        @Test
        @DisplayName("keeps its shape in a box of another, centred")
        void aspect() {
            var image = load("static-rect.json").imageAt(0, 200, 100);
            // The canvas is 100x100 at (50, 0) in a 200x100 image.
            assertColour(0xFFFF0000, image.argb(100, 50), "the middle");
            assertEquals(TRANSPARENT, image.argb(60, 50), "left of the canvas's rectangle");
            assertEquals(TRANSPARENT, image.argb(140, 50), "right of it");
        }

        @Test
        @DisplayName("an ellipse moves along its keyframes")
        void moving() {
            var ellipse = load("moving-ellipse.json");
            var start = ellipse.imageAt(0, 100, 100);
            var middle = ellipse.imageAt(500, 100, 100);
            assertColour(0xFF0000FF, at(start, 20, 50), "starts at the left");
            assertEquals(TRANSPARENT, at(start, 50, 50));
            assertColour(0xFF0000FF, at(middle, 50, 50), "halfway at the middle");
            assertEquals(TRANSPARENT, at(middle, 20, 50));
            var big = ellipse.imageAt(500, 200, 200);
            assertColour(0xFF0000FF, at(big, 50, 50), "and the same at twice the size");
        }

        @Test
        @DisplayName("a child rides on its parent's rotation, its group scaled")
        void parenting() {
            var image = load("parenting.json").imageAt(0, 100, 100);
            // (20, 0) turned a quarter clockwise is (0, 20); about (50, 50) that
            // is (50, 70), and the 10-unit square is 20 units at 200%.
            assertColour(0xFF00FF00, at(image, 50, 70), "where the parent put it");
            assertColour(0xFF00FF00, at(image, 41, 61), "as large as its group made it");
            assertEquals(TRANSPARENT, at(image, 70, 50), "not where it would be unrotated");
        }

        @Test
        @DisplayName("a linear gradient runs between its points, with its opacity stops")
        void gradient() {
            var image = load("gradient.json").imageAt(0, 100, 100);
            var left = at(image, 1, 50);
            var right = at(image, 98, 50);
            assertTrue(((left >>> 16) & 0xFF) > 240 && (left & 0xFF) < 15, "red at the start");
            assertTrue((right & 0xFF) > 240 && ((right >>> 16) & 0xFF) < 15, "blue at the end");
            assertTrue(alpha(left) > 245, "opaque at the start");
            assertEquals(128, alpha(right), 6, "half transparent at the end");
        }

        @Test
        @DisplayName("a gradient turns with its group")
        void rotatedGradient() {
            var image = load("rotated-gradient.json").imageAt(0, 100, 100);
            // Left to right in the group, turned a quarter clockwise: top to bottom.
            var top = at(image, 50, 1);
            var bottom = at(image, 50, 98);
            assertTrue(((top >>> 16) & 0xFF) > 240, "red at the top: " + Integer.toHexString(top));
            assertTrue((bottom & 0xFF) > 240, "blue at the bottom: " + Integer.toHexString(bottom));
            var left = at(image, 1, 50);
            var right = at(image, 98, 50);
            assertColour(left, right, "and the same across");
        }

        @Test
        @DisplayName("a radial gradient spreads from its centre")
        void radial() {
            var image = load("radial.json").imageAt(0, 100, 100);
            var centre = at(image, 50, 50);
            var edge = at(image, 50, 3);
            assertTrue((centre & 0xFF) > 240, "white in the middle");
            assertTrue((edge & 0xFF) < 30, "black at the rim");
        }

        @Test
        @DisplayName("a trim draws a line on")
        void trim() {
            var line = load("trim.json");
            var none = line.imageAt(0, 100, 100);
            var half = line.imageAt(500, 100, 100);
            assertEquals(TRANSPARENT, at(none, 30, 50), "nothing at the start");
            assertColour(0xFF000000, at(half, 30, 50), "the first half halfway through");
            assertEquals(TRANSPARENT, at(half, 70, 50), "and not the second");
            var big = line.imageAt(500, 200, 200);
            assertColour(0xFF000000, at(big, 30, 50), "the same at twice the size");
            assertEquals(TRANSPARENT, at(big, 70, 50));
        }

        @Test
        @DisplayName("a mask cuts a layer out")
        void mask() {
            var image = load("mask.json").imageAt(0, 100, 100);
            assertColour(0xFFFF0000, at(image, 50, 50), "inside the mask");
            assertEquals(TRANSPARENT, at(image, 10, 10), "outside it");
        }

        @Test
        @DisplayName("an alpha matte shows the layer under it only where the matte is")
        void matte() {
            var image = load("matte.json").imageAt(0, 100, 100);
            assertColour(0xFF0000FF, at(image, 50, 50), "inside the matte's circle");
            assertColour(0xFF0000FF, at(image, 32, 50), "near its edge");
            assertEquals(TRANSPARENT, at(image, 10, 10), "outside it");
            assertEquals(TRANSPARENT, at(image, 50, 20), "and the matte itself is not drawn");
        }

        @Test
        @DisplayName("a precomp runs on its own time, from its start")
        void precomp() {
            var comp = load("precomp.json");
            var early = comp.imageAt(340, 100, 100);
            var late = comp.imageAt(1000, 100, 100);
            assertColour(0xFFFF8000, at(early, 35, 50), "its first frame, ten frames in");
            assertColour(0xFFFF8000, at(late, 65, 50), "its twentieth, thirty frames in");
            assertEquals(TRANSPARENT, at(late, 35, 50));
        }

        @Test
        @DisplayName("the whole sticker moves")
        void sticker() {
            var sticker = load("sticker.tgs");
            var first = sticker.imageAt(0, 64, 64);
            var middle = sticker.imageAt(1500, 64, 64);
            var changed = 0;
            for (var y = 0; y < 64; y++) {
                for (var x = 0; x < 64; x++) {
                    if (first.argb(x, y) != middle.argb(x, y)) {
                        changed++;
                    }
                }
            }
            assertTrue(changed > 200, "pixels that moved: " + changed);
            assertNotEquals(0, alpha(at(middle, 50, 50)), "and the face is drawn");
        }

        @Test
        @DisplayName("refuses a size with no pixels in it")
        void emptySize() {
            var rect = load("static-rect.json");
            assertThrows(IllegalArgumentException.class, () -> rect.imageAt(0, 0, 10));
        }
    }

    @Nested
    @DisplayName("refusals")
    class Refusals {

        @Test
        @DisplayName("an expression is code, and is refused")
        void expression() {
            assertThrows(ImageDecodeException.class, () -> load("expression.json"));
        }

        @Test
        @DisplayName("an image layer is a picture, and is refused")
        void image() {
            assertThrows(ImageDecodeException.class, () -> load("image.json"));
        }

        @Test
        @DisplayName("bytes that are neither JSON nor gzip are a decode failure, and the buffer is left alone")
        void garbage() {
            var bytes = ByteBuffer.wrap(new byte[] {1, 2, 3});
            assertThrows(ImageDecodeException.class, () -> VectorAnimation.of(bytes));
            assertEquals(0, bytes.position());
        }

        @Test
        @DisplayName("a gzip that does not inflate is a decode failure")
        void brokenGzip() {
            assertThrows(
                    ImageDecodeException.class,
                    () -> VectorAnimation.of(new byte[] {0x1F, (byte) 0x8B, 8, 0, 0, 0, 0, 0, 0, 0, 1, 2, 3}));
        }
    }

    /// Every feature at once, mid-animation, as a golden: at 1x here and, through
    /// the harness's sweep, at 2x, 1.5x and 1.25x — which is the claim that a
    /// vector animation is the same picture at any density.
    @Test
    @DisplayName("the sticker, halfway through, is the golden")
    void golden() {
        var sticker = load("sticker.json");
        GoldenImage.assertMatches("lottie-sticker", 128, 128, 1f, frame -> sticker.paint(frame, 1500, 0, 0, 128, 128));
    }
}
