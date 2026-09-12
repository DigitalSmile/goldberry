package io.github.digitalsmile.goldberry.paint;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/// The pen, and the pattern it is cut into.
///
/// Both are values with no rasterizer under them, so everything here is about
/// what a caller may write and what it reads back as. Whether the pixels come out
/// dashed is a golden image's question (ADR-0277).
class StrokeTest {

    @Nested
    @DisplayName("what a stroke may be")
    class Strokes {

        @Test
        @DisplayName("the plain pen is one pixel, butt-capped, mitered and solid")
        void defaults() {
            var stroke = Stroke.of(1);

            assertEquals(1, stroke.width());
            assertEquals(Cap.BUTT, stroke.cap());
            assertEquals(Join.MITER, stroke.join());
            assertEquals(Stroke.DEFAULT_MITER_LIMIT, stroke.miterLimit());
            assertEquals(Dash.NONE, stroke.dash());
            assertEquals(Stroke.HAIRLINE, stroke);
        }

        @Test
        @DisplayName("SVG's miter limit default is 4, and it is named rather than written")
        void miterLimitDefault() {
            // A reader who meets a bare 4 in a constructor has no way to tell
            // whether it was chosen or inherited.
            assertEquals(4, Stroke.DEFAULT_MITER_LIMIT);
        }

        @Test
        @DisplayName("a round pen is round at both its ends and its corners")
        void round() {
            // The shape a line chart and an icon both want: a series mitered
            // instead has visible spikes wherever it turns sharply.
            var stroke = Stroke.round(2);

            assertEquals(Cap.ROUND, stroke.cap());
            assertEquals(Join.ROUND, stroke.join());
            assertEquals(2, stroke.width());
        }

        @Test
        @DisplayName("a width of zero or less is a programming error")
        void widthIsPositive() {
            assertThrows(IllegalArgumentException.class, () -> Stroke.of(0));
            assertThrows(IllegalArgumentException.class, () -> Stroke.of(-1));
            assertThrows(IllegalArgumentException.class, () -> Stroke.of(Double.NaN));
            assertThrows(IllegalArgumentException.class, () -> Stroke.of(Double.POSITIVE_INFINITY));
        }

        @Test
        @DisplayName("a miter limit below 1 is refused")
        void miterLimitIsAtLeastOne() {
            // Below 1 a miter is shorter than the bevel it would fall back to,
            // which is not a corner any renderer draws. SVG says the same.
            assertThrows(IllegalArgumentException.class, () -> Stroke.of(1).miterLimit(0.5));
            assertThrows(IllegalArgumentException.class, () -> Stroke.of(1).miterLimit(Double.NaN));
            assertEquals(1, Stroke.of(1).miterLimit(1).miterLimit());
        }

        @Test
        @DisplayName("a null cap, join or dash is refused rather than defaulted")
        void nullsRefused() {
            assertThrows(NullPointerException.class, () -> Stroke.of(1).cap(null));
            assertThrows(NullPointerException.class, () -> Stroke.of(1).join(null));
            assertThrows(NullPointerException.class, () -> Stroke.of(1).dash(null));
        }

        @Test
        @DisplayName("each wither changes one thing and keeps the rest")
        void withers() {
            var stroke = Stroke.round(3).miterLimit(8).dashed(4, 2);

            assertEquals(5, stroke.width(5).width());
            assertEquals(Cap.ROUND, stroke.width(5).cap());
            assertEquals(Dash.of(4, 2), stroke.width(5).dash());
            assertEquals(Join.BEVEL, stroke.join(Join.BEVEL).join());
            assertEquals(8, stroke.join(Join.BEVEL).miterLimit());
            assertEquals(Cap.SQUARE, stroke.cap(Cap.SQUARE).cap());
        }

        @Test
        @DisplayName("two pens written the same way are equal")
        void equality() {
            // The reason Dash holds a List and not a double[]: a record over an
            // array compares identity, so this would fail and a widget caching
            // "have I drawn this?" would cache nothing, silently.
            assertEquals(Stroke.of(2).dashed(4, 2), Stroke.of(2).dashed(4, 2));
            assertEquals(
                    Stroke.of(2).dashed(4, 2).hashCode(),
                    Stroke.of(2).dashed(4, 2).hashCode());
        }

        @Test
        @DisplayName("a stroke knows whether it draws an unbroken line")
        void solid() {
            assertTrue(Stroke.of(1).isSolid());
            assertFalse(Stroke.of(1).dashed(4, 2).isSolid());
        }
    }

    @Nested
    @DisplayName("what a dash may be")
    class Dashes {

        @Test
        @DisplayName("none is empty, solid and of no period")
        void none() {
            assertTrue(Dash.NONE.isSolid());
            assertEquals(List.of(), Dash.NONE.pattern());
            assertEquals(0, Dash.NONE.offset());
            assertEquals(0, Dash.NONE.period());
        }

        @Test
        @DisplayName("the two-number case reads back as it was written")
        void onAndOff() {
            var dash = Dash.of(5, 3);

            assertEquals(List.of(5d, 3d), dash.pattern());
            assertEquals(8, dash.period());
            assertFalse(dash.isSolid());
        }

        @Test
        @DisplayName("an odd pattern repeats to make an even one, as SVG says")
        void oddRepeats() {
            // `5` means 5 on, 5 off; `5 3 2` means 5 on, 3 off, 2 on, 5 off,
            // 3 on, 2 off. Done here so that pattern() reads back what is drawn
            // rather than what was typed.
            assertEquals(List.of(5d, 5d), Dash.of(5).pattern());
            assertEquals(List.of(5d, 3d, 2d, 5d, 3d, 2d), Dash.of(5, 3, 2).pattern());
        }

        @Test
        @DisplayName("an even pattern is left alone")
        void evenIsKept() {
            assertEquals(List.of(5d, 3d, 2d, 1d), Dash.of(5, 3, 2, 1).pattern());
        }

        @Test
        @DisplayName("a pattern of nothing but zeros draws a solid line")
        void allZerosIsSolid() {
            // Nothing is ever skipped, so the rasterizer can be left alone --
            // which matters, because dash state has to be put back afterwards.
            assertTrue(Dash.of(0, 0).isSolid());
            assertFalse(Dash.of(0, 1).isSolid());
        }

        @Test
        @DisplayName("an offset is the animation knob, and the path does not change")
        void offset() {
            var ants = Dash.of(4, 4);

            assertEquals(2, ants.startedAt(2).offset());
            assertEquals(ants.pattern(), ants.startedAt(2).pattern());
            // Advancing by a whole period is the identity, which is what lets a
            // timer stay inside one period forever instead of growing a number
            // until it loses precision.
            assertEquals(8, ants.period());
        }

        @Test
        @DisplayName("a negative or non-finite length is refused")
        void lengthsAreFiniteAndPositive() {
            assertThrows(IllegalArgumentException.class, () -> Dash.of(-1, 2));
            assertThrows(IllegalArgumentException.class, () -> Dash.of(Double.NaN, 2));
            assertThrows(IllegalArgumentException.class, () -> Dash.of(4, 2).startedAt(Double.NaN));
        }

        @Test
        @DisplayName("a null pattern says to use NONE rather than being treated as one")
        void nullPattern() {
            assertThrows(IllegalArgumentException.class, () -> new Dash(null, 0));
        }

        @Test
        @DisplayName("two patterns written the same way are equal")
        void equality() {
            assertEquals(Dash.of(4, 2), Dash.of(4, 2));
            assertEquals(Dash.of(4, 2).hashCode(), Dash.of(4, 2).hashCode());
            assertEquals(Dash.of(4), Dash.of(4, 4));
        }
    }
}
