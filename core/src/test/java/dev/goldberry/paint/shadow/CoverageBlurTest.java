package dev.goldberry.paint.shadow;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Arrays;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/// The three-box Gaussian a text shadow is blurred with.
class CoverageBlurTest {

    private static final int SIZE = 41;

    /// A square of full coverage, `side` wide, in the middle of the mask.
    private static int[] square(int side) {
        var mask = new int[SIZE * SIZE];
        var from = (SIZE - side) / 2;
        for (var y = from; y < from + side; y++) {
            for (var x = from; x < from + side; x++) {
                mask[y * SIZE + x] = 255;
            }
        }
        return mask;
    }

    @Test
    @DisplayName("keeps the coverage, spreading it rather than losing it")
    void conserves() {
        var mask = square(9);
        var before = Arrays.stream(mask).sum();
        CoverageBlur.blur(mask, SIZE, SIZE, 3);
        var after = Arrays.stream(mask).sum();
        assertEquals(before, after, before * 0.02);
    }

    @Test
    @DisplayName("fades an edge both ways, symmetrically")
    void symmetric() {
        var mask = square(9);
        CoverageBlur.blur(mask, SIZE, SIZE, 2);
        var centre = SIZE / 2;
        assertTrue(mask[centre * SIZE + centre] > 200, "the middle stays nearly whole");
        assertTrue(mask[centre * SIZE + centre - 6] > 0, "the fade reaches past the edge");
        assertEquals(mask[centre * SIZE + centre - 6], mask[centre * SIZE + centre + 6], 1);
        assertEquals(mask[(centre - 6) * SIZE + centre], mask[(centre + 6) * SIZE + centre], 1);
        assertEquals(0, mask[0], "the far corner stays empty");
    }

    @Test
    @DisplayName("does nothing at no blur, and refuses a mask of the wrong size")
    void edges() {
        var mask = square(5);
        var copy = mask.clone();
        CoverageBlur.blur(mask, SIZE, SIZE, 0);
        assertArrayEquals(copy, mask);
        assertThrows(IllegalArgumentException.class, () -> CoverageBlur.blur(new int[3], 2, 2, 1));
    }

    @Test
    @DisplayName("picks odd boxes whose passes add up to the Gaussian's variance")
    void boxes() {
        for (var sigma : new double[] {1, 2.5, 6}) {
            var boxes = CoverageBlur.boxes(sigma);
            var variance = 0.0;
            for (var box : boxes) {
                assertEquals(1, box % 2, "an odd box has a centre");
                variance += (box * box - 1) / 12.0;
            }
            assertEquals(sigma * sigma, variance, sigma * sigma * 0.35);
        }
    }
}
