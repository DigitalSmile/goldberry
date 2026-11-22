package dev.goldberry.vector;

import static dev.goldberry.vector.SvgPixels.assertAllEmpty;
import static dev.goldberry.vector.SvgPixels.assertPixel;
import static org.junit.jupiter.api.Assertions.assertAll;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import dev.goldberry.render.model.PhysicalSize;

/// What a document may reach: itself, and `data:` URIs inside it. Never a file
/// or the network.
@DisplayName("Svg, and what it refers to")
class SvgReferencesTest {

    /// A 2 by 2 PNG, opaque green.
    private static final String GREEN_PNG =
            "iVBORw0KGgoAAAANSUhEUgAAAAIAAAACCAYAAABytg0kAAAADklEQVR4nGNg+A+FMAYAQ84H+fei4u8AAAAASUVORK5CYII=";

    private static final PhysicalSize SIZE = new PhysicalSize(4, 4);

    @BeforeAll
    static void library() {
        VectorNativesRequirement.enforce();
    }

    @ParameterizedTest
    @ValueSource(strings = {"href", "xlink:href"})
    @DisplayName("draws a PNG embedded as a data: URI")
    void dataPng(String attribute) {
        try (var svg = Svg.parse("""
                <svg xmlns="http://www.w3.org/2000/svg" xmlns:xlink="http://www.w3.org/1999/xlink"
                     width="4" height="4">
                  <image %s="data:image/png;base64,%s" x="0" y="0" width="4" height="4"/>
                </svg>
                """.formatted(attribute, GREEN_PNG))) {
            var image = svg.render(SIZE);
            assertAll(() -> assertPixel(image, 1, 1, 0xFF00FF00), () -> assertPixel(image, 3, 3, 0xFF00FF00));
        }
    }

    @ParameterizedTest
    @ValueSource(
            strings = {
                "<image href=\"/etc/hostname\" width=\"4\" height=\"4\"/>",
                "<image href=\"file:///usr/share/icons/hicolor/48x48/apps/a.png\" width=\"4\" height=\"4\"/>",
                "<image href=\"picture.png\" width=\"4\" height=\"4\"/>",
                "<image href=\"https://example.invalid/a.png\" width=\"4\" height=\"4\"/>",
                "<use href=\"other.svg#shape\"/>",
            })
    @DisplayName("draws nothing for a reference out of the document")
    void outside(String element) {
        try (var svg =
                Svg.parse("<svg xmlns=\"http://www.w3.org/2000/svg\" width=\"4\" height=\"4\">" + element + "</svg>")) {
            assertAllEmpty(svg.render(SIZE));
        }
    }

    @Test
    @DisplayName("draws a <use> of a shape inside the document")
    void inside() {
        try (var svg = Svg.parse("""
                <svg xmlns="http://www.w3.org/2000/svg" width="4" height="4">
                  <defs><rect id="shape" width="2" height="4" fill="#0000ff"/></defs>
                  <use href="#shape" x="2"/>
                </svg>
                """)) {
            var image = svg.render(SIZE);
            assertAll(() -> SvgPixels.assertEmpty(image, 0, 1), () -> assertPixel(image, 3, 1, 0xFF0000FF));
        }
    }
}
