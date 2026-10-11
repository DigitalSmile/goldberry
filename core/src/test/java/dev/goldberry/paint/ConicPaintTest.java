package dev.goldberry.paint;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import dev.goldberry.RendererRequirement;
import dev.goldberry.css.background.Background;
import dev.goldberry.css.background.BackgroundPosition;
import dev.goldberry.css.background.GradientLayer;
import dev.goldberry.css.background.GradientLayer.ColorStop;
import dev.goldberry.layout.Insets;
import dev.goldberry.layout.Length;

/// `conic-gradient()` in pixels: clockwise from the top of the box's middle,
/// and repeated in phase.
class ConicPaintTest {

    private static final int RED = 0xFFFF0000;

    private static final int BLUE = 0xFF0000FF;

    private static final int GREEN = 0xFF00FF00;

    private static final int YELLOW = 0xFFFFFF00;

    @BeforeAll
    static void requireRenderer() {
        RendererRequirement.enforce();
    }

    /// One 100x40 box at (10, 10), its middle at (60, 30), filled with `layer`.
    private static TestFrames.Target painted(GradientLayer layer) {
        var target = TestFrames.of(120, 60, 1f);
        var box = Box.of()
                .fill(Background.of(0, List.of(layer), BackgroundPosition.ZERO))
                .size(Length.points(100), Length.points(40));
        BoxPainter.paint(
                target.frame(),
                Box.filled(0xFFFFFFFF).padding(Insets.all(Length.points(10))).children(box));
        return target;
    }

    private static GradientLayer conic(double from, boolean repeating, ColorStop... stops) {
        return new GradientLayer.Conic(
                from,
                Length.percent(50),
                Length.percent(50),
                List.of(stops),
                repeating,
                GradientLayer.Interpolation.SRGB);
    }

    private static ColorStop stop(int argb, float percent) {
        return new ColorStop(argb, Length.percent(percent));
    }

    @Test
    @DisplayName("quarters turn clockwise from the top")
    void quarters() {
        var target = painted(conic(
                0,
                false,
                stop(RED, 0),
                stop(RED, 25),
                stop(BLUE, 25),
                stop(BLUE, 50),
                stop(GREEN, 50),
                stop(GREEN, 75),
                stop(YELLOW, 75),
                stop(YELLOW, 100)));

        assertEquals(RED, target.pixel(70, 22), "top right");
        assertEquals(BLUE, target.pixel(70, 38), "bottom right");
        assertEquals(GREEN, target.pixel(50, 38), "bottom left");
        assertEquals(YELLOW, target.pixel(50, 22), "top left");
    }

    @Test
    @DisplayName("`from` turns the whole of it")
    void from() {
        var target = painted(conic(Math.PI / 2, false, stop(RED, 0), stop(RED, 25), stop(BLUE, 25), stop(BLUE, 100)));

        assertEquals(RED, target.pixel(70, 38), "the red quarter starts at the right, so is bottom right");
        assertEquals(BLUE, target.pixel(70, 22), "top right is the end of the turn");
    }

    @Test
    @DisplayName("a repeating one repeats its period, starting where its first stop says")
    void repeating() {
        var halves = painted(conic(0, true, stop(RED, 0), stop(RED, 25), stop(BLUE, 25), stop(BLUE, 50)));
        assertEquals(RED, halves.pixel(70, 22));
        assertEquals(BLUE, halves.pixel(70, 38));
        assertEquals(RED, halves.pixel(50, 38));
        assertEquals(BLUE, halves.pixel(50, 22));

        // Red from 10% to 35% and blue to 60%, repeating: 0 to 10% is blue.
        var shifted = painted(conic(0, true, stop(RED, 10), stop(RED, 35), stop(BLUE, 35), stop(BLUE, 60)));
        assertEquals(BLUE, shifted.pixel(65, 16), "about 20deg round");
        assertEquals(RED, shifted.pixel(70, 22), "about 50deg round");
    }
}
