package io.github.digitalsmile.goldberry.css;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/// The arithmetic four corner radii do, with no painter and no natives.
///
/// It is here rather than in a painting test because it is the half that can be
/// checked exactly: whether a ring grows concentrically, whether a border's inner
/// arc is tighter by half its width, and whether a pair of radii that overrun an
/// edge are scaled in proportion are all questions about numbers. What pixels
/// they produce is [RoundRect]'s, and one golden image says it.
class CornersTest {

    @Nested
    @DisplayName("what a corner may be")
    class Values {

        @Test
        @DisplayName("a negative corner is clamped, not fatal")
        void negativeIsClamped() {
            // [Decoration]'s rule: these arrive from a stylesheet, and §8 drops a
            // bad declaration rather than taking a window down mid-frame.
            assertEquals(Corners.SQUARE, Corners.all(-4));
            assertEquals(new Corners(0, 2, 0, 0), new Corners(-1, 2, -3, -4));
        }

        @Test
        @DisplayName("an infinite corner is a programming error, and says so")
        void infiniteIsRefused() {
            assertThrows(IllegalArgumentException.class, () -> Corners.all(Double.NaN));
            assertThrows(IllegalArgumentException.class, () -> Corners.all(Double.POSITIVE_INFINITY));
        }

        @Test
        @DisplayName("square is four zeros, and uniform is four of anything")
        void squareAndUniform() {
            assertTrue(Corners.SQUARE.isSquare());
            assertTrue(Corners.SQUARE.isUniform());
            assertTrue(Corners.all(8).isUniform());
            assertFalse(Corners.all(8).isSquare());
            assertFalse(new Corners(7, 7, 0, 0).isUniform());
            assertFalse(new Corners(7, 7, 0, 0).isSquare());
        }
    }

    @Nested
    @DisplayName("growing and shrinking")
    class Concentric {

        @Test
        @DisplayName("a ring's radius grows by what it moved out")
        void grow() {
            assertEquals(Corners.all(12), Corners.all(8).grownBy(4));
        }

        @Test
        @DisplayName("but a square corner stays square, so the ring follows the control")
        void growLeavesSquareAlone() {
            // A ring that rounded itself around a sharp box would not follow it.
            assertEquals(new Corners(11, 11, 0, 0), new Corners(7, 7, 0, 0).grownBy(4));
        }

        @Test
        @DisplayName("a border's inner arc is tighter by half its width, floored at square")
        void shrink() {
            assertEquals(new Corners(6.5, 6.5, 0, 0), new Corners(7, 7, 0, 0).shrunkBy(0.5));
            assertEquals(Corners.SQUARE, Corners.all(1).shrunkBy(4));
        }
    }

    @Nested
    @DisplayName("a row of things that meet")
    class Joined {

        @Test
        @DisplayName("the ends keep their corners and the middle keeps none")
        void endsOnly() {
            var radius = Corners.all(7);

            assertEquals(new Corners(7, 0, 0, 7), radius.inRow(true, false), "the first cell");
            assertEquals(Corners.SQUARE, radius.inRow(false, false), "one in the middle");
            assertEquals(new Corners(0, 7, 7, 0), radius.inRow(false, true), "the last");
        }

        @Test
        @DisplayName("a row of one is both ends at once, so it keeps all four")
        void rowOfOne() {
            assertEquals(Corners.all(7), Corners.all(7).inRow(true, true));
        }

        @Test
        @DisplayName("corners that already differ are kept where they are kept")
        void asymmetricSource() {
            // Not `all()`: the rule is about which corners survive, and squaring
            // the source first would hide a mix-up of the two diagonals.
            assertEquals(new Corners(1, 0, 0, 4), new Corners(1, 2, 3, 4).inRow(true, false));
        }
    }

    @Nested
    @DisplayName("fitting inside the box")
    class Fitting {

        @Test
        @DisplayName("corners that fit are left exactly alone")
        void untouched() {
            var corners = new Corners(7, 7, 0, 0);

            assertEquals(corners, corners.fittedTo(200, 32));
        }

        @Test
        @DisplayName("a uniform radius is capped at half the shorter side")
        void uniformIsHalfTheShorterSide() {
            // The rule `border-radius: 9999px` relies on to be a pill rather than
            // a rendering error — and the one the old single-radius painter
            // applied, which is what says no existing drawing moved.
            assertEquals(Corners.all(10), Corners.all(9999).fittedTo(20, 40));
            assertEquals(Corners.all(16), Corners.all(9999).fittedTo(32, 32));
        }

        @Test
        @DisplayName("an edge's pair is scaled together, and so is every other corner")
        void scaledInProportion() {
            // CSS scales all four by the *smallest* edge's factor rather than
            // cutting the offending pair back on its own: corners that stayed in
            // proportion are the point of the rule.
            var fitted = new Corners(30, 10, 0, 0).fittedTo(20, 100);

            assertEquals(15, fitted.topLeft(), 1e-9, "40 of demand into 20 of edge halves it");
            assertEquals(5, fitted.topRight(), 1e-9);
            assertEquals(Corners.SQUARE.bottomRight(), fitted.bottomRight());
        }

        @Test
        @DisplayName("a box with no width has no corners to draw")
        void degenerateBox() {
            assertEquals(Corners.SQUARE, Corners.all(8).fittedTo(0, 40));
            assertEquals(Corners.SQUARE, Corners.all(8).fittedTo(40, -1));
        }
    }
}
