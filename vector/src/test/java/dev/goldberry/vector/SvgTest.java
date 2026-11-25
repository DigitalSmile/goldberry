package dev.goldberry.vector;

import static dev.goldberry.vector.SvgPixels.assertPixel;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.Optional;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import dev.goldberry.image.ImageDecodeException;
import dev.goldberry.render.model.LogicalSize;
import dev.goldberry.render.model.PhysicalSize;

@DisplayName("Svg")
class SvgTest {

    /// Red on the top half, half-transparent blue on the bottom.
    static final String HALVES = """
            <svg xmlns="http://www.w3.org/2000/svg" width="8" height="4" viewBox="0 0 8 4">
              <rect x="0" y="0" width="8" height="2" fill="#ff0000"/>
              <rect x="0" y="2" width="8" height="2" fill="#0000ff" fill-opacity="0.5"/>
            </svg>
            """;

    @BeforeAll
    static void library() {
        VectorNativesRequirement.enforce();
    }

    @Nested
    @DisplayName("parses")
    class Parsing {

        @Test
        @DisplayName("a document's width and height as its natural size, and its view box")
        void size() {
            try (var svg = Svg.parse(HALVES)) {
                assertEquals(new LogicalSize(8, 4), svg.naturalSize());
                assertEquals(Optional.of(new ViewBox(0, 0, 8, 4)), svg.viewBox());
                assertEquals(Svg.DEFAULT_CURRENT_COLOR, svg.currentColor());
            }
        }

        @Test
        @DisplayName("the view box's size when the document names no width or height")
        void viewBoxSize() {
            try (var svg = Svg.parse("<svg xmlns=\"http://www.w3.org/2000/svg\" viewBox=\"10 10 30 20\"/>")) {
                assertEquals(new LogicalSize(30, 20), svg.naturalSize());
            }
        }

        @Test
        @DisplayName("the bounds of what it draws when it names neither")
        void noSize() {
            try (var svg = Svg.parse("""
                    <svg xmlns="http://www.w3.org/2000/svg"><rect x="0" y="0" width="12" height="6"/></svg>
                    """)) {
                assertEquals(new LogicalSize(12, 6), svg.naturalSize());
                assertEquals(Optional.empty(), svg.viewBox());
                assertPixel(svg.render(new PhysicalSize(12, 6)), 6, 3, 0xFF000000);
            }
        }

        @Test
        @DisplayName("the bytes between a buffer's position and limit, without moving it")
        void buffer() {
            var bytes = ("garbage" + HALVES + "garbage").getBytes(StandardCharsets.UTF_8);
            var buffer = ByteBuffer.wrap(bytes, 7, bytes.length - 14);
            var position = buffer.position();
            try (var svg = Svg.parse(buffer)) {
                assertEquals(new LogicalSize(8, 4), svg.naturalSize());
            }
            assertEquals(position, buffer.position());
        }

        @Test
        @DisplayName("a document with a prologue, as an editor saves one")
        void prologue() {
            var document = "\uFEFF<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"no\"?>\n"
                    + "<!-- Created with an editor -->\n"
                    + "<!DOCTYPE svg PUBLIC \"-//W3C//DTD SVG 1.1//EN\" "
                    + "\"http://www.w3.org/Graphics/SVG/1.1/DTD/svg11.dtd\">\n"
                    + HALVES;
            try (var svg = Svg.parse(document)) {
                assertPixel(svg.render(new PhysicalSize(8, 4)), 1, 1, 0xFFFF0000);
            }
        }

        @Test
        @DisplayName("a UTF-8 class name in a stylesheet and on a shape")
        void unicodeClassName() {
            try (var svg = Svg.parse("""
                    <svg xmlns="http://www.w3.org/2000/svg" width="4" height="4">
                      <style>.青 { fill: #0000ff; }</style>
                      <path class="青" d="M0 0h4v4H0z"/>
                    </svg>
                    """)) {
                assertPixel(svg.render(new PhysicalSize(4, 4)), 2, 2, 0xFF0000FF);
            }
        }
    }

    @Nested
    @DisplayName("draws")
    class Drawing {

        @Test
        @DisplayName("premultiplied BGRA at its natural size")
        void natural() {
            try (var svg = Svg.parse(HALVES)) {
                var image = svg.render(new PhysicalSize(8, 4));
                assertEquals(new PhysicalSize(8, 4), image.size());
                assertAll(
                        () -> assertPixel(image, 0, 0, 0xFFFF0000),
                        () -> assertPixel(image, 7, 1, 0xFFFF0000),
                        () -> assertPixel(image, 3, 3, 0x800000FF));
            }
        }

        @Test
        @DisplayName("sharp at any size: an edge on a pixel boundary at four times the size")
        void larger() {
            try (var svg = Svg.parse(HALVES)) {
                var image = svg.render(new PhysicalSize(32, 16));
                assertAll(
                        () -> assertPixel(image, 31, 7, 0xFFFF0000),
                        () -> assertPixel(image, 31, 8, 0x800000FF),
                        () -> assertPixel(image, 0, 15, 0x800000FF));
            }
        }

        @Test
        @DisplayName("the same document again and again, from one parse")
        void again() {
            try (var svg = Svg.parse(HALVES)) {
                var first = svg.render(new PhysicalSize(16, 8));
                var second = svg.render(new PhysicalSize(16, 8));
                for (var y = 0; y < 8; y++) {
                    for (var x = 0; x < 16; x++) {
                        assertEquals(first.argb(x, y), second.argb(x, y));
                    }
                }
            }
        }

        @Test
        @DisplayName("nothing of a <text>: the library has no fonts")
        void noText() {
            try (var svg = Svg.parse("""
                    <svg xmlns="http://www.w3.org/2000/svg" width="40" height="20">
                      <text x="0" y="16" font-size="16">Hi</text>
                    </svg>
                    """)) {
                SvgPixels.assertAllEmpty(svg.render(new PhysicalSize(40, 20)));
            }
        }
    }

    @Nested
    @DisplayName("refuses")
    class Refusing {

        @ParameterizedTest
        @ValueSource(
                strings = {
                    "<html><body><svg></svg></body></html>",
                    "<?xml version=\"1.0\"?><note>not a drawing</note>",
                    "<svg width=\"10\" height=\"10\"",
                    "\u0089PNG\r\n",
                    "just some text",
                })
        @DisplayName("bytes that are not an SVG document, with ImageDecodeException")
        void notSvg(String document) {
            assertThrows(ImageDecodeException.class, () -> Svg.parse(document));
        }

        @Test
        @DisplayName("a document truncated inside its body: parsed and drawn, without a crash")
        void truncated() {
            // What ThorVG makes of the part it read is its own business (v1.1.4
            // draws nothing of it); that it neither crashes nor throws is ours.
            var cut = HALVES.substring(0, HALVES.indexOf("fill=\"#0000ff\"") + 6);
            try (var svg = assertDoesNotThrow(() -> Svg.parse(cut))) {
                assertEquals(
                        new PhysicalSize(8, 4),
                        svg.render(new PhysicalSize(8, 4)).size());
            }
        }

        @Test
        @DisplayName("no bytes at all, with IllegalArgumentException")
        void empty() {
            assertThrows(IllegalArgumentException.class, () -> Svg.parse(""));
            assertThrows(IllegalArgumentException.class, () -> Svg.parse(ByteBuffer.allocate(0)));
        }

        @Test
        @DisplayName("a document over 16 MiB before parsing it")
        void tooLong() {
            var padding = " ".repeat(Svg.MAX_DOCUMENT_BYTES);
            var thrown = assertThrows(ImageDecodeException.class, () -> Svg.parse(padding + HALVES));
            assertTrue(thrown.getMessage().contains("16777216"), thrown.getMessage());
        }

        @Test
        @DisplayName("a raster with no area, over 8192 on a side or over 64 million pixels")
        void rasterSize() {
            try (var svg = Svg.parse(HALVES)) {
                assertAll(
                        () -> assertThrows(IllegalArgumentException.class, () -> svg.render(new PhysicalSize(0, 4))),
                        () -> assertThrows(IllegalArgumentException.class, () -> svg.render(new PhysicalSize(8193, 1))),
                        () -> assertThrows(IllegalArgumentException.class, () -> svg.render(new PhysicalSize(1, 8193))),
                        () -> assertThrows(
                                IllegalArgumentException.class, () -> svg.render(new PhysicalSize(8192, 8192))));
            }
        }

        @Test
        @DisplayName("to draw once closed, and a second close is nothing")
        void closed() {
            var svg = Svg.parse(HALVES);
            svg.close();
            assertThrows(IllegalStateException.class, () -> svg.render(new PhysicalSize(8, 4)));
            assertDoesNotThrow(svg::close);
            assertTrue(svg.toString().endsWith(", closed]"), svg.toString());
        }
    }
}
