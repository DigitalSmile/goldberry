package dev.goldberry.vector;

import static dev.goldberry.vector.SvgPixels.assertPixel;
import static org.junit.jupiter.api.Assertions.assertAll;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import dev.goldberry.render.model.PhysicalSize;

/// `currentColor`, given to a document as the CSS `color` of the box it is drawn
/// in.
@DisplayName("Svg, in currentColor")
class SvgCurrentColorTest {

    private static final int THEME = 0xFF336699;
    private static final PhysicalSize SIZE = new PhysicalSize(8, 8);

    /// An icon in `currentColor`: a filled top half, and a thick stroke along the
    /// bottom.
    private static final String ICON = """
            <svg xmlns="http://www.w3.org/2000/svg" width="8" height="8" viewBox="0 0 8 8" %s>
              <rect x="0" y="0" width="8" height="4" fill="currentColor"/>
              <line x1="0" y1="6" x2="8" y2="6" stroke="currentColor" stroke-width="2"/>
            </svg>
            """;

    @BeforeAll
    static void library() {
        VectorNativesRequirement.enforce();
    }

    @Test
    @DisplayName("fills and strokes in the colour given")
    void given() {
        try (var svg = Svg.parse(ICON.formatted(""), THEME)) {
            var image = svg.render(SIZE);
            assertAll(() -> assertPixel(image, 3, 1, THEME), () -> assertPixel(image, 3, 6, THEME));
        }
    }

    @Test
    @DisplayName("is black when none is given")
    void black() {
        try (var svg = Svg.parse(ICON.formatted(""))) {
            assertPixel(svg.render(SIZE), 3, 1, 0xFF000000);
        }
    }

    @Test
    @DisplayName("keeps the colour's alpha")
    void alpha() {
        try (var svg = Svg.parse(ICON.formatted(""), 0x80FF0000)) {
            assertPixel(svg.render(SIZE), 3, 1, 0x80FF0000);
        }
    }

    @Test
    @DisplayName("gives way to a color the root names itself, as an attribute or in its style")
    void rootNamesItsOwn() {
        try (var attribute = Svg.parse(ICON.formatted("color=\"#00ff00\""), THEME);
                var style = Svg.parse(ICON.formatted("style=\"color:#00ff00\""), THEME)) {
            assertAll(
                    () -> assertPixel(attribute.render(SIZE), 3, 1, 0xFF00FF00),
                    () -> assertPixel(style.render(SIZE), 3, 1, 0xFF00FF00));
        }
    }

    @Test
    @DisplayName("gives way to a color set inside the document, and leaves a named fill alone")
    void insideTheDocument() {
        try (var svg = Svg.parse("""
                <svg xmlns="http://www.w3.org/2000/svg" width="8" height="8">
                  <g color="#00ff00"><rect x="0" y="0" width="8" height="4" fill="currentColor"/></g>
                  <rect x="0" y="4" width="8" height="4" fill="#ff0000"/>
                </svg>
                """, THEME)) {
            var image = svg.render(SIZE);
            assertAll(() -> assertPixel(image, 3, 1, 0xFF00FF00), () -> assertPixel(image, 3, 6, 0xFFFF0000));
        }
    }

    @Test
    @DisplayName("is kept in a viewport of another shape, which is parsed for it")
    void inAnotherShape() {
        try (var svg = Svg.parse(ICON.formatted(""), THEME)) {
            var image = svg.render(new PhysicalSize(16, 8));
            assertPixel(image, 8, 1, THEME);
        }
    }
}
