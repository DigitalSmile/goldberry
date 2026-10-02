package dev.goldberry.paint;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import dev.goldberry.RendererRequirement;
import dev.goldberry.css.Border;
import dev.goldberry.css.Border.Line;
import dev.goldberry.css.Border.Side;
import dev.goldberry.css.Border.Style;
import dev.goldberry.css.Corners;
import dev.goldberry.css.Decoration;

/// `dashed`, `dotted` and `double` borders, in pixels.
///
/// [dev.goldberry.paint.border.BorderPatternTest] says where the dashes and
/// dots go; this says the painter puts them there — ink on a dash, nothing in
/// a gap, and a solid corner where a browser draws one.
class BorderStylePaintTest {

    private static final int RED = 0xFFFF0000;

    private static final int BLUE = 0xFF0000FF;

    @BeforeAll
    static void requireRenderer() {
        RendererRequirement.enforce();
    }

    /// A transparent 60x40 box with `border`, painted at 1x.
    private static TestFrames.Target painted(Corners corners, Border border) {
        var target = TestFrames.of(60, 40, 1f);
        BoxPainter.paint(
                target.frame(),
                Box.of().decoration(Decoration.NONE.corners(corners).border(border)));
        target.end();
        return target;
    }

    @Test
    @DisplayName("a dashed side has dashes and gaps, and a dash on each square corner")
    void dashed() {
        // 2px: six-pixel dashes. The top runs the full 60 with gaps of 4.8, so
        // its dashes are at 0-6, 10.8-16.8, ... and 54-60.
        var target = painted(Corners.SQUARE, Border.all(2, RED, Style.DASHED));

        assertEquals(RED, target.pixel(2, 0), "the corner is inked");
        assertEquals(0, target.alphaAt(8, 0), "a gap");
        assertEquals(RED, target.pixel(13, 1), "the next dash");
        assertEquals(RED, target.pixel(57, 0), "and the far corner");
        // The left side runs between the top and bottom, 2 to 38, with four
        // dashes and three four-pixel gaps: 2-8, 12-18, 22-28, 32-38.
        assertEquals(0, target.alphaAt(0, 9), "a gap down the left");
        assertEquals(RED, target.pixel(1, 14), "and a dash");
        assertEquals(0, target.alphaAt(30, 20), "nothing inside");
    }

    @Test
    @DisplayName("a dotted side is round dots as wide as the side, two widths apart")
    void dotted() {
        // 4px: dots of radius 2, centres at x = 2, 10, 18, ... along y = 2.
        var target = painted(Corners.SQUARE, Border.all(4, RED, Style.DOTTED));

        assertEquals(RED, target.pixel(9, 1), "the middle of a dot");
        assertEquals(0, target.alphaAt(5, 2), "between two dots");
        var edge = target.alphaAt(8, 0);
        assertTrue(edge > 0 && edge < 0xFF, "a dot is round: the corner of its square is part-covered");
        assertEquals(RED, target.pixel(1, 1), "the corner has a dot on it");
    }

    @Test
    @DisplayName("a double side is two bands a third of its width each, with the middle third empty")
    void doubled() {
        var target = painted(Corners.SQUARE, Border.all(6, RED, Style.DOUBLE));

        assertEquals(RED, target.pixel(30, 0), "the outer band");
        assertEquals(RED, target.pixel(30, 1));
        assertEquals(0, target.alphaAt(30, 2), "the gap");
        assertEquals(0, target.alphaAt(30, 3));
        assertEquals(RED, target.pixel(30, 4), "the inner band");
        assertEquals(RED, target.pixel(30, 5));
        assertEquals(0, target.alphaAt(30, 6), "and nothing past the side");
    }

    @Test
    @DisplayName("a dashed side beside solid ones leaves the solid ones as they were")
    void mixed() {
        var solid = new Line(2, BLUE);
        var border = new Border(new Line(2, RED, Style.DASHED), solid, solid, solid);
        var target = painted(Corners.SQUARE, border);

        assertEquals(0, target.alphaAt(8, 0), "a gap in the dashed top");
        assertEquals(BLUE, target.pixel(0, 20), "the left side is solid");
        assertEquals(BLUE, target.pixel(30, 39), "and the bottom");
    }

    @Test
    @DisplayName("on a rounded box a dash runs round the corner")
    void roundedCorner() {
        // A 12px radius: the run's arc is 11px about (12, 12), and its 45°
        // point -- where the top and left sides meet -- is on a dash.
        var target = painted(Corners.all(12), Border.all(2, RED, Style.DASHED));

        assertTrue(target.alphaAt(4, 4) > 0x80, "the middle of the corner is inked");
        assertEquals(0, target.alphaAt(0, 0), "and the box's own corner, outside the curve, is not");
    }

    @Test
    @DisplayName("a groove is drawn solid, the same as `solid`")
    void bevelledIsSolid() {
        var groove = painted(Corners.SQUARE, Border.all(2, RED, Style.GROOVE));

        assertEquals(RED, groove.pixel(30, 0));
        assertEquals(RED, groove.pixel(8, 0), "no gap where a dash's would be");
        assertEquals(0, groove.alphaAt(30, 20));
    }

    @Test
    @DisplayName("a styled side with no ink draws nothing")
    void noInk() {
        var target = painted(Corners.SQUARE, Border.NONE.side(Side.TOP, new Line(2, 0, Style.DASHED)));

        assertEquals(0, target.alphaAt(2, 0));
    }
}
