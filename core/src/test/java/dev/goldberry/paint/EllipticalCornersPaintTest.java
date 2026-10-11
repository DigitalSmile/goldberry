package dev.goldberry.paint;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import dev.goldberry.RendererRequirement;
import dev.goldberry.css.Border;
import dev.goldberry.css.Corners;
import dev.goldberry.css.Decoration;
import dev.goldberry.layout.Length;

/// Corners that are quarter ellipses, percentages of the box, and a ring drawn
/// in a border's style, in pixels.
class EllipticalCornersPaintTest {

    private static final int RED = 0xFFFF0000;

    @BeforeAll
    static void requireRenderer() {
        RendererRequirement.enforce();
    }

    /// A red 100x40 box with `corners`, painted at 1x.
    private static TestFrames.Target filled(Corners corners) {
        var target = TestFrames.of(100, 40, 1f);
        BoxPainter.paint(target.frame(), Box.of().background(RED).decoration(Decoration.NONE.corners(corners)));
        target.end();
        return target;
    }

    @Test
    @DisplayName("50% on a box that is not square is an ellipse, not a pill")
    void percentIsAnEllipse() {
        var target = filled(Corners.all(new Corners.Radius(0, 0, 50, 50)));
        assertEquals(RED, target.pixel(50, 20), "the middle");
        assertEquals(RED, target.pixel(2, 20), "the left end of the long axis");
        assertEquals(RED, target.pixel(50, 1), "the top end of the short axis");
        // Inside a pill of radius 20, outside the ellipse of 50 by 20.
        assertEquals(0, target.alphaAt(10, 5), "the ellipse curves where a pill would not");
        assertEquals(0, target.alphaAt(1, 1), "the corner");
    }

    @Test
    @DisplayName("an elliptical corner reaches its own way along each edge")
    void elliptical() {
        var target = filled(new Corners(
                Corners.Radius.of(30, 10), Corners.Radius.SQUARE, Corners.Radius.SQUARE, Corners.Radius.SQUARE));
        // 30 across the top, 10 down the left.
        assertEquals(0, target.alphaAt(6, 2), "the top edge is still curving at 6");
        assertEquals(RED, target.pixel(1, 12), "the left edge is straight by 12");
        assertEquals(RED, target.pixel(98, 1), "the other corners are square");
    }

    @Test
    @DisplayName("a dashed ring has dashes and gaps outside the box")
    void dashedRing() {
        var target = TestFrames.of(60, 40, 1f);
        var ringed = Box.of()
                .size(Length.points(40), Length.points(20))
                .decoration(Decoration.NONE.outline(2, RED, Border.Style.DASHED, 0));
        BoxPainter.paint(
                target.frame(),
                Box.of()
                        .padding(Length.points(10))
                        .size(Length.points(60), Length.points(40))
                        .children(ringed));
        target.end();
        // The ring is the two pixels outside the 40x20 box at (10, 10): its top
        // row is y = 8 and 9, from x = 8 to 52, dashed at six on and about
        // five off.
        var inked = 0;
        var gaps = 0;
        for (var x = 8; x < 52; x++) {
            if (target.alphaAt(x, 8) > 0x80) {
                inked++;
            } else {
                gaps++;
            }
        }
        var message = "dashes and gaps along the top: " + inked + " inked, " + gaps;
        assertTrue(inked > 10 && gaps > 10, message);
        assertEquals(RED, target.pixel(9, 9), "the ring's corner is a dash");
        assertEquals(0, target.alphaAt(30, 20), "nothing inside the box");
    }
}
