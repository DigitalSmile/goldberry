package dev.goldberry.vector;

import static dev.goldberry.vector.SvgPixels.assertEmpty;
import static dev.goldberry.vector.SvgPixels.assertPixel;
import static org.junit.jupiter.api.Assertions.assertAll;

import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import dev.goldberry.image.Image;
import dev.goldberry.render.model.PhysicalSize;

/// Dashed strokes, and the bound on them: a path whose dashes would number
/// more than a million is stroked undashed, as Skia strokes it, and one with a
/// point at no finite place, or past 10^18, is not stroked at all. Unbounded,
/// ThorVG's dasher appends a dash a step for as long as the length lasts, and a
/// length that subtracting a dash does not change lasts for ever: a 150-byte
/// document took the process's memory.
///
/// Each document draws a 40 by 8 band, `M0 4 H40`, with `10 10` dashes: a
/// dash over x 0..10, a gap over 10..20. Pixel 5 is in the dash and 15 in the
/// gap. A second subpath, off the picture, makes the path as long as a test
/// asks.
@DisplayName("Svg, dashed strokes")
@Timeout(value = 2, unit = TimeUnit.MINUTES)
class SvgDashesTest {

    private static final PhysicalSize SIZE = new PhysicalSize(40, 8);
    private static final int RED = 0xFFFF0000;

    @BeforeAll
    static void library() {
        VectorNativesRequirement.enforce();
    }

    @Test
    @DisplayName("a short path is dashed")
    void dashed() {
        var image = draw("");
        assertAll(() -> assertPixel(image, 5, 4, RED), () -> assertEmpty(image, 15, 4));
    }

    @Test
    @DisplayName("a path of forty thousand dashes is still dashed")
    void underTheBound() {
        // 400 040 units at 20 a pair: 40 004 dashes and gaps.
        var image = draw(" M0 100 H400000");
        assertAll(() -> assertPixel(image, 5, 4, RED), () -> assertEmpty(image, 15, 4));
    }

    @ParameterizedTest(name = "to x = {0}")
    @ValueSource(strings = {"20000000", "1e12", "1e18"})
    @DisplayName("a path of more than a million dashes is drawn undashed")
    void overTheBound(String end) {
        var image = draw(" M0 100 H" + end);
        assertAll(() -> assertPixel(image, 5, 4, RED), () -> assertPixel(image, 15, 4, RED));
    }

    @ParameterizedTest(name = "to x = {0}")
    @ValueSource(strings = {"1e30", "3.4e38", "inf", "Infinity"})
    @DisplayName("a path with a point at no finite place, or past 10^18, is not stroked")
    void unstrokable(String end) {
        var image = draw(" M0 100 H" + end);
        assertAll(() -> assertEmpty(image, 5, 4), () -> assertEmpty(image, 15, 4));
    }

    private static Image draw(String more) {
        try (var svg = Svg.parse("""
                <svg xmlns="http://www.w3.org/2000/svg" width="40" height="8" viewBox="0 0 40 8">
                  <path d="M0 4 H40%s" fill="none" stroke="#f00" stroke-width="4" stroke-dasharray="10 10"/>
                </svg>
                """.formatted(more))) {
            return svg.render(SIZE);
        }
    }
}
