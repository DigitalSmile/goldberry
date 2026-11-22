package dev.goldberry.vector;

import static dev.goldberry.vector.SvgPixels.assertEmpty;
import static dev.goldberry.vector.SvgPixels.assertPixel;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import dev.goldberry.render.model.PhysicalSize;
import dev.goldberry.widgets.core.image.Fit;

/// Where a document lands in a raster whose shape is not its own: by its own
/// `preserveAspectRatio`, or by a [Fit].
@DisplayName("Svg, placed in a raster")
class SvgPlacementTest {

    private static final int RED = 0xFFFF0000;
    private static final int BLUE = 0xFF0000FF;

    /// A 10 by 10 square, red over blue, with `preserveAspectRatio` as given.
    private static Svg square(String preserveAspectRatio) {
        return Svg.parse("""
                <svg xmlns="http://www.w3.org/2000/svg" width="10" height="10" viewBox="0 0 10 10" %s>
                  <rect x="0" y="0" width="10" height="5" fill="#ff0000"/>
                  <rect x="0" y="5" width="10" height="5" fill="#0000ff"/>
                </svg>
                """.formatted(preserveAspectRatio));
    }

    @BeforeAll
    static void library() {
        VectorNativesRequirement.enforce();
    }

    @Nested
    @DisplayName("by its preserveAspectRatio, as a browser draws an SVG image")
    class Viewport {

        @Test
        @DisplayName("xMidYMid meet, the default: whole and centred, the sides empty")
        void meet() {
            try (var svg = square("")) {
                var image = svg.render(new PhysicalSize(20, 10));
                assertAll(
                        () -> assertEmpty(image, 2, 5),
                        () -> assertPixel(image, 6, 2, RED),
                        () -> assertPixel(image, 13, 7, BLUE),
                        () -> assertEmpty(image, 17, 5));
            }
        }

        @Test
        @DisplayName("xMinYMin meet: whole and at the left")
        void meetAtTheStart() {
            try (var svg = square("preserveAspectRatio=\"xMinYMin meet\"")) {
                var image = svg.render(new PhysicalSize(20, 10));
                assertAll(() -> assertPixel(image, 2, 2, RED), () -> assertEmpty(image, 17, 5));
            }
        }

        @Test
        @DisplayName("xMidYMid slice: the raster filled, the top and bottom cut away")
        void slice() {
            try (var svg = square("preserveAspectRatio=\"xMidYMid slice\"")) {
                var image = svg.render(new PhysicalSize(20, 10));
                assertAll(
                        () -> assertPixel(image, 0, 0, RED),
                        () -> assertPixel(image, 19, 4, RED),
                        () -> assertPixel(image, 0, 9, BLUE),
                        () -> assertPixel(image, 19, 5, BLUE));
            }
        }

        @Test
        @DisplayName("none: stretched to the raster")
        void none() {
            try (var svg = square("preserveAspectRatio=\"none\"")) {
                var image = svg.render(new PhysicalSize(20, 10));
                assertAll(
                        () -> assertPixel(image, 0, 2, RED),
                        () -> assertPixel(image, 19, 2, RED),
                        () -> assertPixel(image, 19, 7, BLUE));
            }
        }

        @Test
        @DisplayName("a document with no view box: stretched, as there is nothing to keep the shape of")
        void noViewBox() {
            try (var svg = Svg.parse("""
                    <svg xmlns="http://www.w3.org/2000/svg" width="10" height="10">
                      <rect x="0" y="0" width="10" height="10" fill="#ff0000"/>
                    </svg>
                    """)) {
                var image = svg.render(new PhysicalSize(20, 10));
                assertAll(() -> assertPixel(image, 0, 5, RED), () -> assertPixel(image, 19, 5, RED));
            }
        }

        @Test
        @DisplayName("parses for a viewport only when its shape is not the document's, beyond half a pixel")
        void parsesOnlyForAnotherShape() {
            try (var square = square("");
                    var wide = Svg.parse("<svg xmlns=\"http://www.w3.org/2000/svg\" width=\"100\" height=\"33.33\""
                            + " viewBox=\"0 0 100 33.33\"/>");
                    var stretched = square("preserveAspectRatio=\"none\"")) {
                assertAll(
                        () -> assertFalse(square.needsOwnViewport(new PhysicalSize(10, 10))),
                        () -> assertFalse(square.needsOwnViewport(new PhysicalSize(40, 40))),
                        () -> assertTrue(square.needsOwnViewport(new PhysicalSize(20, 10))),
                        () -> assertTrue(square.needsOwnViewport(new PhysicalSize(11, 10))),
                        () -> assertFalse(wide.needsOwnViewport(new PhysicalSize(300, 100))),
                        () -> assertFalse(stretched.needsOwnViewport(new PhysicalSize(20, 10))));
            }
        }
    }

    @Nested
    @DisplayName("by a Fit")
    class Fitted {

        @Test
        @DisplayName("contain: whole, as large as fits, centred")
        void contain() {
            try (var svg = square("preserveAspectRatio=\"xMinYMin slice\"")) {
                var image = svg.render(new PhysicalSize(20, 10), Fit.CONTAIN);
                assertAll(
                        () -> assertEmpty(image, 2, 5),
                        () -> assertPixel(image, 10, 2, RED),
                        () -> assertPixel(image, 10, 7, BLUE),
                        () -> assertEmpty(image, 17, 5));
            }
        }

        @Test
        @DisplayName("cover: the raster filled, centred and cut")
        void cover() {
            try (var svg = square("")) {
                var image = svg.render(new PhysicalSize(20, 10), Fit.COVER);
                assertAll(
                        () -> assertPixel(image, 0, 0, RED),
                        () -> assertPixel(image, 19, 9, BLUE),
                        () -> assertPixel(image, 19, 4, RED));
            }
        }

        @Test
        @DisplayName("none: one user unit to a pixel, centred")
        void none() {
            try (var svg = square("")) {
                var image = svg.render(new PhysicalSize(20, 20), Fit.NONE);
                assertAll(
                        () -> assertEmpty(image, 2, 2),
                        () -> assertPixel(image, 6, 6, RED),
                        () -> assertPixel(image, 13, 13, BLUE),
                        () -> assertEmpty(image, 17, 17));
            }
        }

        @Test
        @DisplayName("fill: the raster is the document's viewport, so its preserveAspectRatio decides")
        void fill() {
            try (var meet = square("");
                    var none = square("preserveAspectRatio=\"none\"")) {
                var size = new PhysicalSize(20, 10);
                var kept = meet.render(size, Fit.FILL);
                var stretched = none.render(size, Fit.FILL);
                var viewport = meet.render(size);
                assertAll(
                        () -> assertEmpty(kept, 2, 5),
                        () -> assertPixel(stretched, 2, 2, RED),
                        () -> assertEquals(viewport.argb(10, 2), kept.argb(10, 2)));
            }
        }
    }
}
