package dev.goldberry.vector;

import static dev.goldberry.vector.SvgPixels.assertPixel;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import dev.goldberry.render.model.PhysicalSize;

/// Geometry millions of pixels off the picture. ThorVG's rasterizer keeps its
/// cells in 24.8 fixed point, and a point a few million pixels out overflowed
/// the difference of two of them: a line's walk from cell to cell overshot its
/// end and ran until the counter wrapped, hours for one damaged document. The
/// library clamps a point to 2^21 pixels, where every difference fits, which
/// leaves an axis-aligned shape exactly as it was on the picture.
@DisplayName("Svg, geometry far off the picture")
@Timeout(value = 2, unit = TimeUnit.MINUTES)
class SvgFarGeometryTest {

    @BeforeAll
    static void library() {
        VectorNativesRequirement.enforce();
    }

    @Test
    @DisplayName("draws the damaged document that drew for hours, at the size it hung at")
    void theDocumentThatHung() {
        // The robustness tests' reuse.svg after NUMBERS damage: the last <use>
        // scales the star by -2^31 / 10, and at 80 by 1 the root's viewBox puts
        // its points about 27 million pixels out.
        try (var svg = Svg.parse("""
                <svg xmlns="http://www.w1%org/2000/svg" xmlns:xlink="http://www.w3.org/1999/xlink" \
                viewBox="0 . 80 80" width="80" height="1e">
                  <defs>
                    <symbol id="star" viewBox="0 0 10 10" preserveAspectRatio="xMidYMid meet">
                      <path d="M5 0 6.2 3.8 10 3.8 6.9 6.1 8.1 10 5 7.6 1.9 10 3.1 6.1 0 3.8 3.8 3.8Z"/>
                    </symbol>
                    <g id="pair">
                      <use href="#star" width="10" height="10" fill="gold"/>
                      <use xlink:href="#star" x="12" width="10" height="10" fill="teal"/>
                    </g>
                  </defs>
                  <use href="#pair" x="5" y="5"/>
                  <use href="#pair" x="5" y="30" transform="scale(1.5)"/>
                  <use href="#star" x="40" y="40" width="-2147483649" height="20" opacity="0.6"/>
                </svg>
                """)) {
            var size = new PhysicalSize(80, 1);
            assertEquals(80, svg.render(size).width());
        }
    }

    @ParameterizedTest(name = "out to {0}")
    @ValueSource(strings = {"1e6", "3e6", "1e7", "1e9", "3.4e38"})
    @DisplayName("fills the picture with a rectangle reaching far past it on every side, as it is")
    void farRectangle(String far) {
        try (var svg = Svg.parse("""
                <svg xmlns="http://www.w3.org/2000/svg" width="16" height="16">
                  <path d="M-%1$s -%1$s H%1$s V%1$s H-%1$s Z" fill="#00f"/>
                </svg>
                """.formatted(far))) {
            var image = svg.render(new PhysicalSize(16, 16));
            assertAll(
                    () -> assertPixel(image, 0, 0, 0xFF0000FF),
                    () -> assertPixel(image, 8, 8, 0xFF0000FF),
                    () -> assertPixel(image, 15, 15, 0xFF0000FF));
        }
    }

    @ParameterizedTest(name = "height {0}")
    @ValueSource(strings = {"-inf", "inf", "NaN", "1e19", "1e38", "3.4e38", "-3e38"})
    @DisplayName("draws a stroked rounded rectangle of no finite height, or one near float's end, without its stroke")
    void farStrokes(String height) {
        // The robustness tests' shapes.svg after NUMBERS damage stroked a
        // rounded <rect> of height -inf until memory ran out; past 10^18 the
        // stroker's own sums overflow the same way. Such a stroke is not drawn.
        try (var svg = Svg.parse("""
                <svg xmlns="http://www.w3.org/2000/svg" width="16" height="16">
                  <rect x="2" y="2" width="12" height="%1$s" rx="3" ry="2" fill="#c33" stroke="#300"/>
                  <circle cx="8" cy="8" r="%1$s" fill="none" stroke="#000"/>
                  <rect x="4" y="4" width="8" height="8" fill="none" stroke="#00f" stroke-width="2"/>
                </svg>
                """.formatted(height))) {
            var image = svg.render(new PhysicalSize(16, 16));
            // The finite square beside them is stroked as ever.
            assertPixel(image, 4, 8, 0xFF0000FF);
        }
    }

    @ParameterizedTest(name = "{0}")
    @ValueSource(
            strings = {
                "M8 8 L-3e7 1 L3e7 -2e7 Z",
                "M8 8 L-2e9 0.5 L2e9 15.5 Z",
                "M0 0 C1e30 -1e30 -1e30 1e30 16 16 Z",
                "M8 8 L inf 8 L 8 inf Z",
                "M8 8 L -inf 4 L 4 -inf Z",
            })
    @DisplayName("draws a slanted shape with points millions of pixels out, or at no finite place")
    void farSlants(String path) {
        try (var svg = Svg.parse("""
                <svg xmlns="http://www.w3.org/2000/svg" width="16" height="16">
                  <path d="%s" fill="#0a0"/>
                </svg>
                """.formatted(path))) {
            assertEquals(16, svg.render(new PhysicalSize(16, 16)).width());
        }
    }
}
