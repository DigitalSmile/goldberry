package dev.goldberry.paint;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import dev.goldberry.RendererRequirement;
import dev.goldberry.image.Image;
import dev.goldberry.paint.slice.NinePatch;

/// `Frame.drawNineSlice`: a painter draws a picture cut in nine, and each of
/// the nine lands where the cut says.
///
/// Not a golden image, for [DrawImageTest]'s reason. The picture is 3 × 3
/// pixels of nine flat colours cut one pixel in from every side, so every piece
/// is one colour and every assertion is a colour that can be named.
class DrawNineSliceTest {

    private static final int RED = 0xFFFF0000;
    private static final int GREEN = 0xFF00FF00;
    private static final int BLUE = 0xFF0000FF;
    private static final int WHITE = 0xFFFFFFFF;
    private static final int BLACK = 0xFF000000;
    private static final int CYAN = 0xFF00FFFF;
    private static final int MAGENTA = 0xFFFF00FF;
    private static final int YELLOW = 0xFFFFFF00;
    private static final int GREY = 0xFF808080;

    private static final NinePatch CUT = NinePatch.of(1, 1, 1, 1);

    private TestFrames.Target target;

    @BeforeEach
    void setUp() {
        RendererRequirement.enforce();
        target = TestFrames.of(40, 40, 1.0f, 0);
    }

    /// The nine colours, row by row: corners red, green, blue, white; edges
    /// black (top), cyan (left), magenta (right), yellow (bottom); grey middle.
    private static Image nine() {
        return Image.ofArgb(3, 3, new int[] {
            RED, BLACK, GREEN,
            CYAN, GREY, MAGENTA,
            BLUE, YELLOW, WHITE
        });
    }

    @Test
    @DisplayName("the corners stay one pixel, and the edges and middle stretch between them")
    void stretched() {
        target.frame().drawNineSlice(nine(), CUT.withWidths(2, 2, 2, 2), 10, 10, 20, 10);
        target.end();

        // Corners, two pixels square at the rectangle's corners.
        assertEquals(RED, target.pixel(10, 10));
        assertEquals(RED, target.pixel(11, 11));
        assertEquals(GREEN, target.pixel(29, 10));
        assertEquals(BLUE, target.pixel(10, 19));
        assertEquals(WHITE, target.pixel(29, 19));
        // Edges, stretched along their length.
        assertEquals(BLACK, target.pixel(12, 10));
        assertEquals(BLACK, target.pixel(27, 11));
        assertEquals(YELLOW, target.pixel(20, 18));
        assertEquals(CYAN, target.pixel(10, 15));
        assertEquals(MAGENTA, target.pixel(28, 15));
        // The middle.
        assertEquals(GREY, target.pixel(12, 12));
        assertEquals(GREY, target.pixel(27, 17));
        // And nothing outside.
        assertEquals(0, target.alphaAt(9, 10));
        assertEquals(0, target.alphaAt(30, 15));
        assertEquals(0, target.alphaAt(20, 20));
    }

    @Test
    @DisplayName("without fill the middle shows what is under it")
    void noFill() {
        target.frame().drawNineSlice(nine(), CUT.withWidths(2, 2, 2, 2).withFill(false), 10, 10, 20, 10);
        target.end();

        assertEquals(0, target.alphaAt(20, 15));
        assertEquals(BLACK, target.pixel(20, 10));
    }

    @Test
    @DisplayName("at 200% a corner drawn one logical pixel wide is two device pixels")
    void followsTheDisplayScale() {
        var retina = TestFrames.of(40, 40, 2.0f, 0);
        retina.frame().drawNineSlice(nine(), CUT, 5, 5, 10, 10);
        retina.end();

        // Logical (5, 5) is device (10, 10); the corner is 1 logical = 2 device.
        assertEquals(RED, retina.pixel(10, 10));
        assertEquals(RED, retina.pixel(11, 11));
        assertEquals(BLACK, retina.pixel(12, 10));
        assertEquals(WHITE, retina.pixel(29, 29));
    }

    @Test
    @DisplayName("a rectangle with no area is refused, and alpha 0 draws nothing")
    void edges() {
        var frame = target.frame();
        assertThrows(IllegalArgumentException.class, () -> frame.drawNineSlice(nine(), CUT, 0, 0, 0, 10));
        assertThrows(IllegalArgumentException.class, () -> frame.drawNineSlice(nine(), CUT, 0, 0, 10, 10, 2));

        frame.drawNineSlice(nine(), CUT, 0, 0, 10, 10, 0);
        target.end();
        assertEquals(0, target.alphaAt(0, 0));
    }
}
