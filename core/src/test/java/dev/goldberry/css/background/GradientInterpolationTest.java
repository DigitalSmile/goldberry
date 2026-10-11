package dev.goldberry.css.background;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import dev.goldberry.css.background.GradientLayer.ColorStop;
import dev.goldberry.css.background.GradientLayer.Direction;
import dev.goldberry.css.background.GradientLayer.Interpolation;
import dev.goldberry.css.value.colour.ColourMixing;
import dev.goldberry.css.value.colour.ColourMixing.HueMethod;
import dev.goldberry.css.value.colour.ColourSpace;
import dev.goldberry.layout.Length;
import dev.goldberry.paint.Gradient;

/// Hints, colour spaces and the conic gradient, resolved against a box: the
/// stops each turns into for a rasterizer that blends in sRGB, in straight
/// lines.
class GradientInterpolationTest {

    private static final int RED = 0xFFFF0000;

    private static final int BLUE = 0xFF0000FF;

    private static final Interpolation OKLCH = new Interpolation(ColourSpace.OKLCH, HueMethod.SHORTER);

    private static Gradient.Linear toRight(List<ColorStop> stops, Interpolation interpolation) {
        var layer = new GradientLayer.Linear(new Direction.Angle(Math.PI / 2), stops, false, interpolation);
        return assertInstanceOf(Gradient.Linear.class, layer.resolve(0, 0, 100, 10, BackgroundPosition.ZERO));
    }

    /// The colour a ramp has at `offset`, as the rasterizer would blend it.
    private static int at(Gradient gradient, double offset) {
        return new Ramp(0, 1, gradient.stops()).colourAt(offset);
    }

    private static int red(int argb) {
        return (argb >> 16) & 0xFF;
    }

    @Nested
    @DisplayName("a hint")
    class Hints {

        @Test
        @DisplayName("moves where the two colours are half and half")
        void moves() {
            var gradient = toRight(
                    List.of(new ColorStop(RED, null), ColorStop.hint(Length.percent(25)), new ColorStop(BLUE, null)),
                    Interpolation.SRGB);

            var middle = red(at(gradient, 0.25));
            assertTrue(Math.abs(middle - 128) <= 3, "half red at the hint: " + middle);
            assertTrue(red(at(gradient, 0.5)) < 90, "and mostly blue by the middle");
            assertEquals(Ramp.SAMPLES + 1, gradient.stops().size(), "drawn as a curve of stops");
        }

        @Test
        @DisplayName("half way is no hint at all")
        void halfWay() {
            assertEquals(0.5, Ramp.curve(0.5, 0.5), 1e-9);
            assertEquals(0.25, Ramp.curve(0.25, 0.5), 1e-9);
            assertEquals(1, Ramp.curve(0.1, 0), "at the first colour: the second from the start");
            assertEquals(0, Ramp.curve(0.9, 1), "at the second: the first until the end");
        }

        @Test
        @DisplayName("is not a colour: it takes no place in sharing out the stops around it")
        void notAStop() {
            var gradient = toRight(
                    List.of(
                            new ColorStop(RED, null),
                            new ColorStop(0xFF00FF00, null),
                            ColorStop.hint(Length.percent(60)),
                            new ColorStop(BLUE, null)),
                    Interpolation.SRGB);

            assertEquals(0xFF00FF00, at(gradient, 0.5), "green half way, as without the hint");
        }

        @Test
        @DisplayName("is between two colours, never at an end or beside another")
        void refused() {
            var hint = ColorStop.hint(Length.percent(50));
            var red = new ColorStop(RED, null);
            var blue = new ColorStop(BLUE, null);
            var direction = new Direction.Angle(0);
            assertThrows(
                    IllegalArgumentException.class,
                    () -> new GradientLayer.Linear(direction, List.of(hint, red, blue), false));
            assertThrows(
                    IllegalArgumentException.class,
                    () -> new GradientLayer.Linear(direction, List.of(red, blue, hint), false));
            assertThrows(
                    IllegalArgumentException.class,
                    () -> new GradientLayer.Linear(direction, List.of(red, hint, hint, blue), false));
        }
    }

    @Nested
    @DisplayName("a colour space")
    class Spaces {

        @Test
        @DisplayName("sRGB is the rasterizer's own blend, and needs no extra stops")
        void srgb() {
            var gradient = toRight(List.of(new ColorStop(RED, null), new ColorStop(BLUE, null)), Interpolation.SRGB);
            assertEquals(2, gradient.stops().size());
        }

        @Test
        @DisplayName("another is drawn as stops along its curve, half way being its own mix")
        void oklch() {
            var gradient = toRight(List.of(new ColorStop(RED, null), new ColorStop(BLUE, null)), OKLCH);

            assertEquals(Ramp.SAMPLES + 1, gradient.stops().size());
            var expected = ColourMixing.mix(RED, BLUE, 0.5, ColourSpace.OKLCH, HueMethod.SHORTER);
            assertEquals(expected, at(gradient, 0.5));
        }

        @Test
        @DisplayName("a hard edge stays hard")
        void hardEdge() {
            var gradient = toRight(
                    List.of(new ColorStop(RED, Length.percent(50)), new ColorStop(BLUE, Length.percent(50))), OKLCH);
            assertEquals(2, gradient.stops().size());
        }
    }

    @Nested
    @DisplayName("a conic gradient")
    class Conic {

        private static Gradient.Conic resolve(GradientLayer.Conic layer) {
            return assertInstanceOf(Gradient.Conic.class, layer.resolve(10, 20, 100, 50, BackgroundPosition.ZERO));
        }

        private static GradientLayer.Conic conic(double from, boolean repeating, ColorStop... stops) {
            return new GradientLayer.Conic(
                    from, Length.percent(50), Length.percent(50), List.of(stops), repeating, Interpolation.SRGB);
        }

        @Test
        @DisplayName("turns once round the box's middle, from the top")
        void plain() {
            var gradient = resolve(conic(0, false, new ColorStop(RED, null), new ColorStop(BLUE, null)));

            assertEquals(60, gradient.cx(), 1e-9);
            assertEquals(45, gradient.cy(), 1e-9);
            assertEquals(-Math.PI / 2, gradient.angle(), 1e-9, "the rasterizer's zero is the positive x axis");
            assertEquals(1, gradient.repeat());
            assertEquals(List.of(new Gradient.Stop(0, RED), new Gradient.Stop(1, BLUE)), gradient.stops());
        }

        @Test
        @DisplayName("holds its end colours outside its stops, round the turn")
        void padded() {
            var gradient = resolve(conic(
                    Math.PI / 2,
                    false,
                    new ColorStop(RED, Length.percent(25)),
                    new ColorStop(BLUE, Length.percent(50))));

            assertEquals(0, gradient.angle(), 1e-9, "from 90deg is the rasterizer's zero");
            assertEquals(RED, at(gradient, 0.1));
            assertEquals(BLUE, at(gradient, 0.9));
        }

        @Test
        @DisplayName("repeats its first period as many times as fit in a turn, in phase")
        void repeating() {
            var gradient = resolve(conic(
                    0,
                    true,
                    new ColorStop(RED, Length.percent(10)),
                    new ColorStop(RED, Length.percent(35)),
                    new ColorStop(BLUE, Length.percent(35)),
                    new ColorStop(BLUE, Length.percent(60))));

            assertEquals(2, gradient.repeat(), 1e-9, "a period of half a turn");
            // The period's offsets run from the turn's start: 0..10% of the
            // turn is the end of the previous period, blue.
            assertEquals(BLUE, at(gradient, 0.1));
            assertEquals(RED, at(gradient, 0.4));
            assertEquals(BLUE, at(gradient, 0.9));
        }

        @Test
        @DisplayName("is placed by angles, never lengths")
        void lengths() {
            assertThrows(
                    IllegalArgumentException.class,
                    () -> conic(0, false, new ColorStop(RED, Length.points(4)), new ColorStop(BLUE, null)));
        }
    }
}
