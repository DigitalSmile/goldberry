package io.github.digitalsmile.goldberry.paint;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/// A ramp, as a value — what it may be and what it reads back as.
///
/// The one behaviour here that is not bookkeeping is [Gradient#fade]: a fade to
/// `0x00000000` is a fade to transparent *black*, so a green faded that way goes
/// through grey. That is the classic wrong gradient, it is invisible in code
/// review, and it is what this type exists to make unwritable (ADR-0207,
/// ADR-0277).
class GradientTest {

    @Nested
    @DisplayName("stops")
    class Stops {

        @Test
        @DisplayName("a stop sits between 0 and 1")
        void bounds() {
            assertThrows(IllegalArgumentException.class, () -> new Gradient.Stop(-0.1, 0xFF000000));
            assertThrows(IllegalArgumentException.class, () -> new Gradient.Stop(1.1, 0xFF000000));
            assertThrows(IllegalArgumentException.class, () -> new Gradient.Stop(Double.NaN, 0xFF000000));
            assertEquals(0, new Gradient.Stop(0, 0).offset());
            assertEquals(1, new Gradient.Stop(1, 0).offset());
        }

        @Test
        @DisplayName("stops come back in ascending order however they were written")
        void sorted() {
            // So that two gradients describing the same ramp are equal, whichever
            // order their stops were listed in.
            var ramp = Gradient.linear(
                    0,
                    0,
                    10,
                    0,
                    new Gradient.Stop(1, 0xFF0000FF),
                    new Gradient.Stop(0, 0xFFFF0000),
                    new Gradient.Stop(0.5, 0xFF00FF00));

            assertEquals(
                    List.of(0d, 0.5d, 1d),
                    ramp.stops().stream().map(Gradient.Stop::offset).toList());
        }

        @Test
        @DisplayName("two stops at one offset keep the order they were written in")
        void stableAtTheSameOffset() {
            // A hard edge between two colours is written this way, and reordering
            // it would turn the edge around.
            var ramp = Gradient.linear(
                    0,
                    0,
                    10,
                    0,
                    new Gradient.Stop(0, 0xFFFF0000),
                    new Gradient.Stop(0.5, 0xFFFF0000),
                    new Gradient.Stop(0.5, 0xFF0000FF),
                    new Gradient.Stop(1, 0xFF0000FF));

            assertEquals(0xFFFF0000, ramp.stops().get(1).argb());
            assertEquals(0xFF0000FF, ramp.stops().get(2).argb());
        }

        @Test
        @DisplayName("a gradient with no stops fills with nothing, so it is refused")
        void atLeastOne() {
            assertThrows(IllegalArgumentException.class, () -> Gradient.linear(0, 0, 10, 0));
        }

        @Test
        @DisplayName("the stop list is a copy, not the caller's")
        void copied() {
            var stops = new ArrayList<Gradient.Stop>();
            stops.add(new Gradient.Stop(0, 0xFFFFFFFF));
            var ramp = new Gradient.Linear(0, 0, 10, 0, stops);

            stops.add(new Gradient.Stop(1, 0xFF000000));

            assertEquals(1, ramp.stops().size());
        }
    }

    @Nested
    @DisplayName("geometry")
    class Geometry {

        @Test
        @DisplayName("a non-finite endpoint is refused")
        void finite() {
            // Blend2D accepts a NaN here and fills the shape with nothing, which
            // is indistinguishable from arithmetic that went wrong upstream.
            var stop = new Gradient.Stop(0, 0xFFFFFFFF);
            assertThrows(IllegalArgumentException.class, () -> Gradient.linear(Double.NaN, 0, 10, 0, stop));
            assertThrows(
                    IllegalArgumentException.class, () -> Gradient.linear(0, 0, Double.POSITIVE_INFINITY, 0, stop));
        }

        @Test
        @DisplayName("the two points read back as they were given")
        void endpoints() {
            var ramp = Gradient.linear(1, 2, 3, 4, new Gradient.Stop(0, 0));

            assertEquals(1, ramp.x1());
            assertEquals(2, ramp.y1());
            assertEquals(3, ramp.x2());
            assertEquals(4, ramp.y2());
        }
    }

    @Nested
    @DisplayName("fading to transparent")
    class Fade {

        @Test
        @DisplayName("a fade keeps its colour and drops only the alpha")
        void keepsTheHue() {
            // The whole reason fade() exists: written by hand as a fade to
            // 0x00000000, this green would pass through grey.
            var green = 0xFF00FF00;
            var ramp = Gradient.fade(0, 0, 0, 100, green);

            assertEquals(List.of(new Gradient.Stop(0, green), new Gradient.Stop(1, 0x0000FF00)), ramp.stops());
        }

        @Test
        @DisplayName("a fade from an already-translucent colour keeps that colour too")
        void fromTranslucent() {
            var ramp = Gradient.fade(0, 0, 0, 100, 0x80123456);

            assertEquals(0x80123456, ramp.stops().getFirst().argb());
            assertEquals(0x00123456, ramp.stops().getLast().argb());
        }
    }

    @Nested
    @DisplayName("as a value")
    class AsAValue {

        @Test
        @DisplayName("two ramps written the same way are equal")
        void equality() {
            assertEquals(Gradient.fade(0, 0, 0, 10, 0xFF00FF00), Gradient.fade(0, 0, 0, 10, 0xFF00FF00));
            assertEquals(
                    Gradient.fade(0, 0, 0, 10, 0xFF00FF00).hashCode(),
                    Gradient.fade(0, 0, 0, 10, 0xFF00FF00).hashCode());
            assertNotEquals(Gradient.fade(0, 0, 0, 10, 0xFF00FF00), Gradient.fade(0, 0, 0, 11, 0xFF00FF00));
        }

        @Test
        @DisplayName("a ramp written in either stop order is the same ramp")
        void orderDoesNotMatter() {
            var first = new Gradient.Stop(0, 0xFFFF0000);
            var last = new Gradient.Stop(1, 0xFF0000FF);

            assertEquals(Gradient.linear(0, 0, 10, 0, first, last), Gradient.linear(0, 0, 10, 0, last, first));
        }
    }
}
