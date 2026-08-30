package io.github.digitalsmile.goldberry.paint;

import static org.junit.jupiter.api.Assertions.assertEquals;

import io.github.digitalsmile.goldberry.RendererRequirement;
import io.github.digitalsmile.goldberry.css.Corners;
import io.github.digitalsmile.goldberry.css.Decoration;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/// A radius per corner, in pixels.
///
/// [io.github.digitalsmile.goldberry.css.CornersTest] checks the arithmetic; this
/// checks that the arithmetic reaches the rasterizer — that a corner asked to be
/// square is square, and that the three beside it are unaffected. The bug this
/// pins is the one ADR-0216 fixed: `group-box-title` asked for `7px 7px 0 0`, the
/// engine dropped the whole declaration, and a header spilled square shoulders
/// out of its rounded frame.
class CornerPaintTest {

    private static final int RED = 0xFFFF0000;

    @BeforeAll
    static void requireRenderer() {
        RendererRequirement.enforce();
    }

    /// A 100x100 fill with `corners`, and what it left at each corner pixel.
    private static TestFrames.Target painted(Corners corners) {
        var target = TestFrames.of(100, 100, 1f);
        BoxPainter.paint(target.frame(),
                Box.filled(RED).decoration(Decoration.NONE.corners(corners)));
        return target;
    }

    @Test
    @DisplayName("a rounded corner leaves its corner pixel empty and a square one fills it")
    void oneCornerAtATime() {
        var target = painted(new Corners(20, 0, 0, 0));

        assertEquals(0, target.pixel(1, 1) >>> 24, "top-left is rounded at 20px");
        assertEquals(RED, target.pixel(98, 1), "top-right was asked to stay square");
        assertEquals(RED, target.pixel(98, 98), "bottom-right too");
        assertEquals(RED, target.pixel(1, 98), "and bottom-left");
    }

    @Test
    @DisplayName("`7px 7px 0 0` is the group-box header: round on top, square underneath")
    void headerCorners() {
        var target = painted(new Corners(7, 7, 0, 0));

        assertEquals(0, target.pixel(0, 0) >>> 24, "the top corners follow the frame");
        assertEquals(0, target.pixel(99, 0) >>> 24);
        assertEquals(RED, target.pixel(0, 99), "the bottom ones carry on into the body");
        assertEquals(RED, target.pixel(99, 99));
    }

    @Test
    @DisplayName("a uniform radius still rounds all four, which is every control in the catalog")
    void uniformIsUnchanged() {
        // The shared path: one drawing serves both, so this is what says the
        // hundred boxes that write one number did not move.
        var target = painted(Corners.all(20));

        for (var corner : new int[][] {{1, 1}, {98, 1}, {98, 98}, {1, 98}}) {
            assertEquals(0, target.pixel(corner[0], corner[1]) >>> 24,
                    "corner " + corner[0] + "," + corner[1] + " is rounded");
        }
        assertEquals(RED, target.pixel(50, 50), "and the middle is still filled");
    }

    @Test
    @DisplayName("square corners take the rectangle path, and fill every corner pixel")
    void squareIsARectangle() {
        var target = painted(Corners.SQUARE);

        assertEquals(RED, target.pixel(0, 0));
        assertEquals(RED, target.pixel(99, 99));
    }
}
