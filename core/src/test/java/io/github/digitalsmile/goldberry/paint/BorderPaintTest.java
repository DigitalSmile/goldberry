package io.github.digitalsmile.goldberry.paint;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.ArrayList;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import io.github.digitalsmile.goldberry.RendererRequirement;
import io.github.digitalsmile.goldberry.css.Border;
import io.github.digitalsmile.goldberry.css.Border.Line;
import io.github.digitalsmile.goldberry.css.Border.Side;
import io.github.digitalsmile.goldberry.css.Corners;
import io.github.digitalsmile.goldberry.css.Decoration;
import io.github.digitalsmile.goldberry.layout.FlexDirection;
import io.github.digitalsmile.goldberry.layout.Length;
import io.github.digitalsmile.goldberry.render.model.LogicalRect;

/// A border per side, in pixels — ADR-0505.
///
/// [io.github.digitalsmile.goldberry.css.BorderStyleTest] checks which side a
/// declaration reaches; this checks what the painter puts there. The golden
/// image is the picture and these are the pixels it is made of, named: where a
/// lone side stops, where two widths meet, and whether a border in one colour
/// has a seam down its mitre.
class BorderPaintTest {

    private static final int RED = 0xFFFF0000;
    private static final int GREEN = 0xFF00FF00;
    private static final int BLUE = 0xFF0000FF;
    private static final int YELLOW = 0xFFFFFF00;

    @BeforeAll
    static void requireRenderer() {
        RendererRequirement.enforce();
    }

    /// A transparent `width` x `height` box with `border`, painted at 1x.
    private static TestFrames.Target painted(int width, int height, Corners corners, Border border) {
        var target = TestFrames.of(width, height, 1f);
        BoxPainter.paint(
                target.frame(),
                Box.of().decoration(Decoration.NONE.corners(corners).border(border)));
        target.end();
        return target;
    }

    @Test
    @DisplayName("a lone `border-bottom` is the bottom row of pixels and nothing else")
    void loneSide() {
        var target = painted(40, 20, Corners.SQUARE, Border.NONE.side(Side.BOTTOM, new Line(1, RED)));

        assertEquals(RED, target.pixel(0, 19), "it runs to the left edge");
        assertEquals(RED, target.pixel(20, 19));
        assertEquals(RED, target.pixel(39, 19), "and to the right one");
        assertEquals(0, target.alphaAt(20, 18), "one pixel tall");
        assertEquals(0, target.alphaAt(0, 0), "and no other side drew");
        assertEquals(0, target.alphaAt(39, 10));
    }

    @Test
    @DisplayName("four colours meet on the diagonal from the outer corner to the inner one")
    void fourColoursMitre() {
        var border = new Border(new Line(4, RED), new Line(4, GREEN), new Line(4, BLUE), new Line(4, YELLOW));
        var target = painted(20, 20, Corners.SQUARE, border);

        assertEquals(RED, target.pixel(10, 1));
        assertEquals(GREEN, target.pixel(18, 10));
        assertEquals(BLUE, target.pixel(10, 18));
        assertEquals(YELLOW, target.pixel(1, 10));
        // Either side of the top-left mitre, which runs from (0, 0) to (4, 4).
        assertEquals(RED, target.pixel(3, 0), "above the diagonal is the top");
        assertEquals(YELLOW, target.pixel(0, 3), "below it is the left");
        assertEquals(0, target.alphaAt(10, 10), "and the inside is not painted");
    }

    @Test
    @DisplayName("two widths in one colour are one fill, so the mitre between them has no seam")
    void oneColourHasNoSeam() {
        // A 2px top against a 6px left: the mitre runs from (0, 0) to (6, 2),
        // through pixel (3, 1). Two anti-aliased fills meeting there would each
        // cover part of it and leave it short of opaque.
        var border = Border.NONE.side(Side.TOP, new Line(2, RED)).side(Side.LEFT, new Line(6, RED));
        var target = painted(20, 20, Corners.SQUARE, border);

        assertEquals(RED, target.pixel(3, 1), "a pixel the mitre crosses is fully covered");
        assertEquals(RED, target.pixel(15, 1), "the top is 2px");
        assertEquals(0, target.alphaAt(15, 2));
        assertEquals(RED, target.pixel(5, 15), "the left is 6px");
        assertEquals(0, target.alphaAt(6, 15));
        assertEquals(0, target.alphaAt(19, 19), "and the two absent sides drew nothing");
    }

    @Test
    @DisplayName("with a radius, a side follows the curve and does not fill the corner outside it")
    void roundedSide() {
        var target = painted(40, 40, Corners.all(10), Border.NONE.side(Side.LEFT, new Line(4, RED)));

        assertEquals(0, target.alphaAt(0, 0), "the corner pixel is outside a 10px curve");
        assertEquals(0, target.alphaAt(0, 39));
        assertEquals(RED, target.pixel(1, 20), "the straight run is the side's width");
        assertEquals(0, target.alphaAt(5, 20));
        assertEquals(0, target.alphaAt(38, 20), "the right side has no width and drew nothing");
    }

    @Test
    @DisplayName("four equal sides written one at a time draw exactly what `border` draws")
    void uniformIsTheOldStroke() {
        // Uniform is detected from the value, not from how it was written, so a
        // stylesheet that spelled a 2px border as four longhands still takes the
        // one-stroke drawing every golden in the corpus is made of.
        var sideBySide = Border.NONE
                .side(Side.TOP, new Line(2, RED))
                .side(Side.RIGHT, new Line(2, RED))
                .side(Side.BOTTOM, new Line(2, RED))
                .side(Side.LEFT, new Line(2, RED));
        var written = painted(40, 30, Corners.all(8), sideBySide);
        var shorthand = TestFrames.of(40, 30, 1f);
        BoxPainter.paint(
                shorthand.frame(), Box.of().decoration(Decoration.NONE.radius(8).border(2, RED)));
        shorthand.end();

        for (var y = 0; y < 30; y++) {
            for (var x = 0; x < 40; x++) {
                assertEquals(shorthand.pixel(x, y), written.pixel(x, y), "at " + x + ", " + y);
            }
        }
    }

    /// Layout is the other half of the decision: a border is drawn over the
    /// padding and never reaches Yoga, which is what every bordered widget's
    /// padding already assumes — so a side must not move a child either.
    @Test
    @DisplayName("a side takes no room: the child of a box with a 10px left border does not move")
    void takesNoRoom() {
        var target = TestFrames.of(100, 40, 1f);
        var bordered = Box.of()
                .direction(FlexDirection.ROW)
                .size(Length.points(100), Length.points(40))
                .decoration(Decoration.NONE.border(Border.NONE.side(Side.LEFT, new Line(10, RED))))
                .children(Box.filled(BLUE).size(Length.points(20), Length.points(20)));
        var placed = new ArrayList<LogicalRect>();
        BoxPainter.forEachBox(target.frame(), bordered, (box, layout) -> placed.add(layout));
        target.end();

        assertEquals(0, placed.get(1).left(), 1e-6, "Yoga was not told about the border");
        assertEquals(0, placed.get(1).top(), 1e-6);
        assertEquals(20, placed.get(1).width(), 1e-6);
    }
}
