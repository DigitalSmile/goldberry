package dev.goldberry.paint;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/// A point inside a path's fill: what a canvas hit-tests the shapes it drew
/// with, by the fill rule they were drawn with.
@DisplayName("a path's fill contains")
class PathContainsTest {

    @Test
    @DisplayName("the points inside a rectangle, and none outside it")
    void rectangle() {
        var path = Path.rect(10, 10, 20, 10);
        assertTrue(path.contains(15, 15));
        assertFalse(path.contains(5, 15));
        assertFalse(path.contains(15, 25));
        assertFalse(path.contains(Double.NaN, 15), "no point at all");
    }

    @Test
    @DisplayName("the points of a circle, through its curves")
    void circle() {
        var path = Path.circle(50, 50, 20);
        assertTrue(path.contains(50, 50));
        assertTrue(path.contains(50 + 19.5, 50), "just inside the curve");
        assertFalse(path.contains(50 + 14.5, 50 + 14.5), "outside, near the diagonal: 20.5 from the centre");
        assertTrue(path.contains(50 + 14, 50 + 14), "inside, near the diagonal: 19.8 from the centre");
    }

    @Test
    @DisplayName("a hole under evenodd, and under nonzero only where the inner ring winds the other way")
    void rings() {
        var same = Path.builder()
                .moveTo(0, 0)
                .lineTo(40, 0)
                .lineTo(40, 40)
                .lineTo(0, 40)
                .close()
                .moveTo(10, 10)
                .lineTo(30, 10)
                .lineTo(30, 30)
                .lineTo(10, 30)
                .close()
                .build();
        assertFalse(same.contains(20, 20, true), "evenodd: two crossings");
        assertTrue(same.contains(20, 20, false), "nonzero: both wind the same way");
        assertTrue(same.contains(5, 5, true), "the ring itself either way");

        var opposite = Path.builder()
                .moveTo(0, 0)
                .lineTo(40, 0)
                .lineTo(40, 40)
                .lineTo(0, 40)
                .close()
                .moveTo(10, 10)
                .lineTo(10, 30)
                .lineTo(30, 30)
                .lineTo(30, 10)
                .close()
                .build();
        assertFalse(opposite.contains(20, 20, false), "nonzero: the windings cancel");
    }

    @Test
    @DisplayName("an open sub-path as if it were closed, as a fill draws it")
    void openSubPath() {
        var triangle = Path.builder().moveTo(0, 0).lineTo(30, 0).lineTo(0, 30).build();
        assertTrue(triangle.contains(5, 5));
        assertFalse(triangle.contains(25, 25));
        assertFalse(Path.EMPTY.contains(0, 0));
    }
}
