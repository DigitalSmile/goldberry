package dev.goldberry.paint;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import dev.goldberry.RendererRequirement;
import dev.goldberry.css.Corners;
import dev.goldberry.css.Decoration;
import dev.goldberry.css.background.Background;
import dev.goldberry.css.background.BackgroundPosition;
import dev.goldberry.css.background.GradientLayer;
import dev.goldberry.css.background.GradientLayer.ColorStop;
import dev.goldberry.css.background.GradientLayer.Direction;
import dev.goldberry.layout.Insets;
import dev.goldberry.layout.Length;

/// Gradient backgrounds, in pixels: a layer is drawn over the box's colour, in
/// the box's shape, where the layout put the box.
class BackgroundPaintTest {

    private static final int WHITE = 0xFFFFFFFF;

    private static final int RED = 0xFFFF0000;

    private static final int BLUE = 0xFF0000FF;

    private static final int GREEN = 0xFF00FF00;

    @BeforeAll
    static void requireRenderer() {
        RendererRequirement.enforce();
    }

    /// A white page with one 100x40 box at (10, 10) filled with `fill`.
    private static TestFrames.Target painted(Background fill, Corners corners) {
        var target = TestFrames.of(120, 60, 1f);
        var box = Box.of()
                .fill(fill)
                .size(Length.points(100), Length.points(40))
                .decoration(Decoration.NONE.corners(corners));
        BoxPainter.paint(
                target.frame(),
                Box.filled(WHITE).padding(Insets.all(Length.points(10))).children(box));
        return target;
    }

    private static GradientLayer toRight(boolean repeating, ColorStop... stops) {
        return new GradientLayer.Linear(new Direction.Angle(Math.PI / 2), List.of(stops), repeating);
    }

    private static int red(int argb) {
        return (argb >> 16) & 0xFF;
    }

    @Test
    @DisplayName("a linear gradient runs across the box where the layout put it")
    void linear() {
        var fill = Background.of(
                0,
                List.of(toRight(false, new ColorStop(RED, null), new ColorStop(BLUE, null))),
                BackgroundPosition.ZERO);
        var target = painted(fill, Corners.SQUARE);

        assertTrue(red(target.pixel(11, 30)) > 240, "red at the box's left edge, not the frame's");
        assertTrue(red(target.pixel(108, 30)) < 15, "blue at its right edge");
        var middle = red(target.pixel(60, 30));
        assertTrue(middle > 100 && middle < 155, "and between them in the middle: " + middle);
        assertEquals(WHITE, target.pixel(5, 30), "nothing outside the box");
    }

    @Test
    @DisplayName("a layer is drawn over the colour, and the first layer over the second")
    void layers() {
        // A hard-edged half: red to 50%, transparent after. Over a blue layer,
        // over a green colour.
        var half = toRight(
                false,
                new ColorStop(RED, Length.percent(0)),
                new ColorStop(RED, Length.percent(50)),
                new ColorStop(0x00FF0000, Length.percent(50)),
                new ColorStop(0x00FF0000, Length.percent(100)));
        var blue = toRight(false, new ColorStop(BLUE, null), new ColorStop(BLUE, null));
        var target = painted(Background.of(GREEN, List.of(half, blue), BackgroundPosition.ZERO), Corners.SQUARE);

        assertEquals(RED, target.pixel(20, 30), "the first layer, on top");
        assertEquals(BLUE, target.pixel(90, 30), "the second, where the first is transparent");
    }

    @Test
    @DisplayName("a repeating gradient repeats, and `background-position` moves it")
    void repeatingAndMoved() {
        var stripes = toRight(
                true,
                new ColorStop(RED, Length.points(0)),
                new ColorStop(RED, Length.points(5)),
                new ColorStop(BLUE, Length.points(5)),
                new ColorStop(BLUE, Length.points(10)));
        var still = painted(Background.of(0, List.of(stripes), BackgroundPosition.ZERO), Corners.SQUARE);
        var moved = painted(Background.of(0, List.of(stripes), new BackgroundPosition(5, 0)), Corners.SQUARE);

        assertEquals(RED, still.pixel(12, 30), "0-5 of each period is red");
        assertEquals(BLUE, still.pixel(17, 30), "and 5-10 blue");
        assertEquals(RED, still.pixel(92, 30), "all the way across");
        assertEquals(BLUE, moved.pixel(12, 30), "moved by half a period, the colours swap");
        assertEquals(RED, moved.pixel(17, 30));
    }

    @Test
    @DisplayName("a radial gradient is centred in the box and fades out to its edge")
    void radial() {
        var layer = new GradientLayer.Radial(
                true,
                GradientLayer.Extent.CLOSEST_SIDE,
                null,
                null,
                Length.percent(50),
                Length.percent(50),
                List.of(new ColorStop(RED, null), new ColorStop(BLUE, null)),
                false);
        var target = painted(Background.of(0, List.of(layer), BackgroundPosition.ZERO), Corners.SQUARE);

        assertTrue(red(target.pixel(60, 30)) > 240, "red in the middle");
        assertTrue(red(target.pixel(60, 11)) < 30, "blue at the closest side, 20 away");
        assertTrue(red(target.pixel(11, 30)) < 15, "and past it the end colour holds");
    }

    @Test
    @DisplayName("a repeating radial gradient whose stops start out from the centre repeats inwards too")
    void repeatingRadialInwards() {
        // Red at 10px to blue at 20px, repeating: a ten-pixel period, so 8.5px
        // from the centre is 85% of the way from red to blue, and 3.5px is 35%.
        var layer = new GradientLayer.Radial(
                true,
                null,
                Length.points(40),
                Length.points(40),
                Length.percent(50),
                Length.percent(50),
                List.of(new ColorStop(RED, Length.points(10)), new ColorStop(BLUE, Length.points(20))),
                true);
        var target = painted(Background.of(0, List.of(layer), BackgroundPosition.ZERO), Corners.SQUARE);

        assertTrue(red(target.pixel(68, 30)) < 100, "mostly blue 8.5px out: " + red(target.pixel(68, 30)));
        assertTrue(red(target.pixel(63, 30)) > 130, "mostly red 3.5px out: " + red(target.pixel(63, 30)));
        assertTrue(red(target.pixel(71, 30)) > 200, "and nearly red again 11.5px out, past the first stop");
    }

    @Test
    @DisplayName("a gradient on a rounded box is rounded")
    void rounded() {
        var fill = Background.of(
                0,
                List.of(toRight(false, new ColorStop(RED, null), new ColorStop(RED, null))),
                BackgroundPosition.ZERO);
        var target = painted(fill, Corners.all(12));

        assertEquals(WHITE, target.pixel(10, 10), "the corner outside the curve is the page");
        assertEquals(RED, target.pixel(60, 30));
    }

    @Test
    @DisplayName("opacity fades a gradient's stops with the rest of the box")
    void faded() {
        var fill = Background.of(
                0,
                List.of(toRight(false, new ColorStop(RED, null), new ColorStop(RED, null))),
                BackgroundPosition.ZERO);
        var target = TestFrames.of(120, 60, 1f);
        var box =
                Box.of().fill(fill).size(Length.points(100), Length.points(40)).opacity(0.5);
        BoxPainter.paint(
                target.frame(),
                Box.filled(WHITE).padding(Insets.all(Length.points(10))).children(box));

        var pixel = target.pixel(60, 30);
        assertEquals(0xFF, red(pixel));
        assertTrue(((pixel >> 8) & 0xFF) > 100, "half red over white is pink, not red");
    }
}
