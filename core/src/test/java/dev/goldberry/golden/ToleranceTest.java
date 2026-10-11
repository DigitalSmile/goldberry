package dev.goldberry.golden;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/// What each golden tolerance admits.
///
/// Read more:
/// [Comparing with a tolerance](https://goldberry.dev/docs/guide/testing.html#comparing-with-a-tolerance).
@DisplayName("Golden tolerances")
class ToleranceTest {

    private static final int CUBE = 200 * 160;
    private static final int FRAME = 1200 * 900;
    private static final int FRAME_AT_2X = 2400 * 2000;

    @Test
    @DisplayName("RASTER admits 2% within two levels, and nothing beyond them in a widget-sized golden")
    void rasterOnAWidget() {
        assertTrue(Tolerance.RASTER.admits(0, 0, CUBE));
        assertTrue(Tolerance.RASTER.admits(640, 0, CUBE), "2% within the channel tolerance");
        assertFalse(Tolerance.RASTER.admits(641, 0, CUBE), "one more than 2%");
        assertFalse(Tolerance.RASTER.admits(1, 1, CUBE), "a single pixel beyond two levels");
    }

    @Test
    @DisplayName("RASTER admits the few stroke pixels another CPU outlines differently in a frame, and not a line")
    void rasterOnAFrame() {
        assertTrue(Tolerance.RASTER.admits(4, 4, FRAME), "two icons' edges on Apple Silicon");
        assertTrue(Tolerance.RASTER.admits(13, 13, FRAME_AT_2X), "the same icons at 2x");
        assertTrue(Tolerance.RASTER.admits(10, 10, FRAME), "one in 100,000");
        assertFalse(Tolerance.RASTER.admits(11, 11, FRAME), "one more");
        assertFalse(Tolerance.RASTER.admits(16, 16, FRAME), "a 16px caret that went missing");
    }

    @Test
    @DisplayName("GPU admits the two flipped silhouette pixels lavapipe drew, and not a silhouette's worth")
    void gpuAdmitsAFewFlips() {
        assertTrue(Tolerance.GPU.admits(2, 2, CUBE), "what lavapipe drew against Metal's golden");
        assertTrue(Tolerance.GPU.admits(32, 32, CUBE), "one in a thousand");
        assertFalse(Tolerance.GPU.admits(33, 33, CUBE), "one more");
        assertFalse(Tolerance.GPU.admits(400, 400, CUBE), "a whole silhouette moved");
    }

    @Test
    @DisplayName("GPU admits a lit fill rounded one level apart everywhere, which RASTER does not")
    void gpuAdmitsShadingRounding() {
        assertTrue(Tolerance.GPU.admits(16_674, 0, CUBE), "what NVIDIA drew against Metal's golden");
        assertTrue(Tolerance.GPU.admits(CUBE, 0, CUBE), "every pixel, within two levels");
        assertFalse(Tolerance.RASTER.admits(16_674, 0, CUBE), "Blend2D's fills are exact, so its cap stays");
    }

    @Test
    @DisplayName("a tolerance that could not be read as one is refused")
    void refusesNonsense() {
        assertThrows(IllegalArgumentException.class, () -> new Tolerance(-1, 0.02, 0));
        assertThrows(IllegalArgumentException.class, () -> new Tolerance(256, 0.02, 0));
        assertThrows(IllegalArgumentException.class, () -> new Tolerance(2, 0.01, 0.02), "stray above differing");
        assertThrows(IllegalArgumentException.class, () -> new Tolerance(2, Double.NaN, 0));
        assertThrows(IllegalArgumentException.class, () -> Tolerance.RASTER.admits(1, 2, CUBE));
        assertThrows(IllegalArgumentException.class, () -> Tolerance.RASTER.admits(0, 0, 0));
    }
}
