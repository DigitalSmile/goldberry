package io.github.digitalsmile.goldberry.paint;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import io.github.digitalsmile.goldberry.RendererRequirement;
import io.github.digitalsmile.goldberry.image.Image;
import io.github.digitalsmile.goldberry.render.model.PhysicalRect;

/// `Frame.drawImage` — ADR-0283.
///
/// Not a golden image, for [LayerTest]'s reason: what matters here is *which*
/// pixels move where, and a picture of a blit says only that something was drawn.
/// Every image below is four pixels of four flat colours, so every assertion is a
/// colour that can be named.
///
/// The images are built with `Image.ofArgb` rather than decoded. Decoding is
/// [io.github.digitalsmile.goldberry.image.ImageTest]'s subject; what is under
/// test here is the blit, and a decoder in the middle of it would be a second
/// thing that could be wrong.
class DrawImageTest {

    private static final int RED = 0xFFFF0000;
    private static final int GREEN = 0xFF00FF00;
    private static final int BLUE = 0xFF0000FF;
    private static final int WHITE = 0xFFFFFFFF;

    private TestFrames.Target target;

    @BeforeEach
    void setUp() {
        RendererRequirement.enforce();
        // Synchronous, so the pixels are final when the frame ends rather than
        // queued behind Blend2D's workers.
        target = TestFrames.of(40, 40, 1.0f, 0);
    }

    /// Red, green, blue and white in the four quadrants of a 2×2 image.
    private static Image quadrants() {
        return Image.ofArgb(2, 2, new int[] {RED, GREEN, BLUE, WHITE});
    }

    @Test
    @DisplayName("an image at its natural size is one image pixel per device pixel")
    void drawsAtNaturalSize() {
        target.frame().drawImage(quadrants(), 4, 4);
        target.end();

        // At 100% the four pixels land at 4,4 through 5,5 -- and nowhere else.
        assertEquals(RED, target.pixel(4, 4));
        assertEquals(GREEN, target.pixel(5, 4));
        assertEquals(BLUE, target.pixel(4, 5));
        assertEquals(WHITE, target.pixel(5, 5));
        assertEquals(0, target.alphaAt(6, 4), "and nothing past its right edge");
        assertEquals(0, target.alphaAt(4, 6), "or below it");
    }

    @Test
    @DisplayName("at 200% the natural size is half as many logical units, and still crisp")
    void naturalSizeFollowsTheDisplayScale() {
        // The bug ADR-0157 found in layers, asked of images: a 2x2 image drawn at
        // "natural size" on a 2x display must cover two DEVICE pixels, which is one
        // logical unit -- not two logical units, which would be four device pixels
        // of interpolated mush.
        var retina = TestFrames.of(40, 40, 2.0f, 0);
        retina.frame().drawImage(quadrants(), 2, 2);
        retina.end();

        // Logical (2, 2) is device (4, 4) at 200%.
        assertEquals(RED, retina.pixel(4, 4));
        assertEquals(GREEN, retina.pixel(5, 4));
        assertEquals(BLUE, retina.pixel(4, 5));
        assertEquals(WHITE, retina.pixel(5, 5));
        assertEquals(0, retina.alphaAt(6, 4), "two device pixels wide, not four");
    }

    @Test
    @DisplayName("a stated size scales the image into it")
    void drawsIntoAStatedRectangle() {
        target.frame().drawImage(quadrants(), 0, 0, 20, 20);
        target.end();

        // Scaling filters, so only the middle of each quadrant is worth asserting:
        // the seams between them are interpolated on purpose.
        assertEquals(RED, target.pixel(2, 2));
        assertEquals(GREEN, target.pixel(17, 2));
        assertEquals(BLUE, target.pixel(2, 17));
        assertEquals(WHITE, target.pixel(17, 17));
    }

    @Test
    @DisplayName("a source rectangle draws that part of the image and no other")
    void drawsACrop() {
        // The bottom-left pixel only -- blue -- over a 10x10 square. A crop that
        // was ignored would put red there, and one whose axes were swapped would
        // put green.
        target.frame().drawImage(quadrants(), PhysicalRect.of(0, 1, 1, 1), 0, 0, 10, 10, 1);
        target.end();

        for (var y = 0; y < 10; y++) {
            for (var x = 0; x < 10; x++) {
                assertEquals(BLUE, target.pixel(x, y), "(" + x + ", " + y + ") is the cropped pixel");
            }
        }
        assertEquals(0, target.alphaAt(10, 10), "and the crop stops where it was told");
    }

    @Test
    @DisplayName("an alpha fades the image and is put back afterwards")
    void fadesAndRestores() {
        var image = Image.ofArgb(1, 1, new int[] {RED});
        target.frame().drawImage(image, 0, 0, 4, 4, 0.5);
        // The next thing drawn did not ask to be faded. If the global alpha were
        // left on the context, this square would come out half-strength too --
        // which is the bug the `finally` in `drawImage` is there for, and it would
        // be invisible in any test that drew only one thing.
        target.frame().fillRect(10, 10, 4, 4, RED);
        target.end();

        var faded = target.pixel(1, 1);
        assertEquals(0x80, faded >>> 24, "half-transparent red");
        assertEquals(RED, target.pixel(11, 11), "and the frame is not left faded");
    }

    @Test
    @DisplayName("an alpha of zero draws nothing at all")
    void skipsAnInvisibleImage() {
        target.frame().drawImage(quadrants(), 0, 0, 10, 10, 0);
        target.end();

        assertEquals(0, target.alphaAt(0, 0));
        assertEquals(0, target.alphaAt(5, 5));
    }

    @Test
    @DisplayName("a translucent image composites rather than replacing")
    void compositesTranslucentPixels() {
        target.frame().fillRect(0, 0, 10, 10, RED);
        target.frame().drawImage(Image.ofArgb(1, 1, new int[] {0x80FFFFFF}), 0, 0, 10, 10);
        target.end();

        // Half-white over red is a pink that is still fully opaque. A blit that
        // replaced the pixels instead of blending them would leave alpha at 0x80.
        var blended = target.pixel(5, 5);
        assertEquals(0xFF, blended >>> 24, "opaque, because something opaque is underneath");
        assertTrue(
                (blended & 0xFF) > 0x70, "and white has lightened the blue channel: " + Integer.toHexString(blended));
    }

    @Test
    @DisplayName("a source rectangle outside the image is refused rather than quietly shrunk")
    void refusesACropOutsideTheImage() {
        var image = quadrants();
        var frame = target.frame();

        // Blend2D would intersect this with the image and draw the overlap, which
        // is a smaller picture in the wrong place and no report at all.
        var thrown = assertThrows(
                IllegalArgumentException.class,
                () -> frame.drawImage(image, PhysicalRect.of(1, 1, 2, 2), 0, 0, 10, 10, 1));
        assertTrue(thrown.getMessage().contains("not inside"), thrown.getMessage());

        assertThrows(
                IllegalArgumentException.class,
                () -> frame.drawImage(image, PhysicalRect.of(0, 0, 0, 2), 0, 0, 10, 10, 1),
                "an empty crop draws nothing and means nothing");
        assertThrows(
                IllegalArgumentException.class,
                () -> frame.drawImage(image, PhysicalRect.of(-1, 0, 1, 1), 0, 0, 10, 10, 1),
                "a negative origin is outside too");
        target.end();
    }

    @Test
    @DisplayName("a destination that is not a positive rectangle is refused")
    void refusesAnEmptyDestination() {
        var image = quadrants();
        var frame = target.frame();

        assertThrows(IllegalArgumentException.class, () -> frame.drawImage(image, 0, 0, 0, 10));
        assertThrows(IllegalArgumentException.class, () -> frame.drawImage(image, 0, 0, 10, -1));
        assertThrows(IllegalArgumentException.class, () -> frame.drawImage(image, 0, 0, 10, 10, 2));
        assertThrows(NullPointerException.class, () -> frame.drawImage(null, 0, 0));
        target.end();
    }

    @Test
    @DisplayName("an image can be drawn twice, because drawing does not consume it")
    void drawsTheSameImageTwice() {
        // The practical difference between a value and a handle: nothing here is
        // closed, borrowed or invalidated, so a cached image is drawn every frame
        // for the life of the application (ADR-0283).
        var image = quadrants();
        target.frame().drawImage(image, 0, 0);
        target.frame().drawImage(image, 10, 10);
        target.end();

        assertEquals(RED, target.pixel(0, 0));
        assertEquals(RED, target.pixel(10, 10));
        assertEquals(WHITE, target.pixel(11, 11));
    }
}
