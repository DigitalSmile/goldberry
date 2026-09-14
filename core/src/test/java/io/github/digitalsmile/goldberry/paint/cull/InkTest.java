package io.github.digitalsmile.goldberry.paint.cull;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import io.github.digitalsmile.goldberry.css.value.Affine;
import io.github.digitalsmile.goldberry.paint.Clip;

/// The arithmetic a skipped subtree rests on.
///
/// Worth a unit test of its own rather than a golden image, for the reason
/// [ADR-0313] gives: a culler that is wrong by a pixel drops a row off the bottom
/// of a list, and the frame it drops it from looks perfectly plausible.
@DisplayName("Ink")
class InkTest {

    @Nested
    @DisplayName("the empty one")
    class Nothing {

        @Test
        @DisplayName("draws nowhere, and so overlaps nothing at all")
        void noneOverlapsNothing() {
            assertTrue(Ink.NONE.isEmpty());
            assertFalse(Ink.NONE.overlaps(Clip.NONE), "not even the clip that admits everything");
        }

        @Test
        @DisplayName("is the identity of a union, which is why it is inverted rather than zero")
        void noneIsTheIdentity() {
            var box = Ink.of(10, 20, 30, 40);

            assertEquals(box, Ink.NONE.union(box));
            assertEquals(box, box.union(Ink.NONE));
            // A zero-sized rectangle at the origin would drag this to (0, 0),
            // which is the bug the inverted edges exist to rule out.
            assertEquals(box, box.union(Ink.of(0, 0, 0, 0)));
        }

        @Test
        @DisplayName("a zero-sized box is empty however far from the origin it is")
        void zeroSizedIsEmpty() {
            assertTrue(Ink.of(500, 500, 0, 10).isEmpty());
            assertTrue(Ink.of(500, 500, 10, 0).isEmpty());
        }
    }

    @Nested
    @DisplayName("combining")
    class Combining {

        @Test
        @DisplayName("a union is the smallest rectangle covering both")
        void union() {
            assertEquals(new Ink(0, 0, 40, 60), Ink.of(0, 0, 10, 10).union(Ink.of(30, 50, 10, 10)));
        }

        @Test
        @DisplayName("a shift moves all four edges, and nothing at all when it is zero")
        void shift() {
            var box = Ink.of(0, 0, 10, 10);

            assertEquals(new Ink(5, -3, 15, 7), box.shiftedBy(5, -3));
            assertSame(box, box.shiftedBy(0, 0), "the overwhelming majority of nodes, and it must cost nothing");
        }

        @Test
        @DisplayName("a scale maps the corners; the identity maps nothing")
        void map() {
            var box = Ink.of(10, 10, 10, 10);

            assertSame(box, box.mappedBy(Affine.IDENTITY));
            assertEquals(new Ink(20, 20, 40, 40), box.mappedBy(Affine.scale(2, 2)));
        }

        /// The case two corners would get wrong: a rotated rectangle's bounding
        /// box is bigger than the rectangle, and a culler that took the diagonal
        /// alone would cut its corners off.
        @Test
        @DisplayName("a rotation takes the box around the diamond, not the diagonal")
        void rotationIsConservative() {
            var square = Ink.of(-5, -5, 10, 10);

            var turned = square.mappedBy(Affine.rotate(Math.PI / 4));

            var half = 5 * Math.sqrt(2);
            assertEquals(-half, turned.left(), 1e-9);
            assertEquals(-half, turned.top(), 1e-9);
            assertEquals(half, turned.right(), 1e-9);
            assertEquals(half, turned.bottom(), 1e-9);
        }
    }

    @Nested
    @DisplayName("against a clip")
    class Overlapping {

        private final Clip viewport = Clip.of(0, 0, 100, 100);

        @Test
        @DisplayName("inside, straddling and containing all overlap")
        void overlaps() {
            assertTrue(Ink.of(10, 10, 10, 10).overlaps(viewport), "wholly inside");
            assertTrue(Ink.of(-10, -10, 20, 20).overlaps(viewport), "straddling a corner");
            assertTrue(Ink.of(-50, -50, 500, 500).overlaps(viewport), "swallowing it whole");
            assertTrue(Ink.of(50, 50, 10, 10).overlaps(Clip.NONE), "and nothing clips at all");
        }

        @Test
        @DisplayName("past any edge does not, which is the whole licence to skip it")
        void pastAnEdge() {
            assertFalse(Ink.of(0, -30, 100, 20).overlaps(viewport), "above");
            assertFalse(Ink.of(0, 120, 100, 20).overlaps(viewport), "below");
            assertFalse(Ink.of(-30, 0, 20, 100).overlaps(viewport), "left");
            assertFalse(Ink.of(120, 0, 20, 100).overlaps(viewport), "right");
        }

        /// One pixel either side of the decision, because this is where a row
        /// goes missing: `bottom == clip.top()` is the row directly above the
        /// viewport and it puts no pixel in it, and one more pixel down does.
        @Test
        @DisplayName("a touching edge is outside and one pixel over it is not")
        void edgesAreExclusive() {
            assertFalse(Ink.of(0, -20, 100, 20).overlaps(viewport), "its bottom edge is the clip's top");
            assertTrue(Ink.of(0, -19.9, 100, 20).overlaps(viewport), "a tenth of a pixel of it is inside");
            assertFalse(Ink.of(100, 0, 20, 100).overlaps(viewport), "its left edge is the clip's right");
        }
    }
}
