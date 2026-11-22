package dev.goldberry.vector.document;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import dev.goldberry.image.ImageDecodeException;
import dev.goldberry.vector.ViewBox;

@DisplayName("RootTag")
class RootTagTest {

    private static byte[] bytes(String text) {
        return text.getBytes(StandardCharsets.UTF_8);
    }

    private static String text(byte[] bytes) {
        return new String(bytes, StandardCharsets.UTF_8);
    }

    @Nested
    @DisplayName("finds the root")
    class Finding {

        @Test
        @DisplayName("at the very start")
        void bare() {
            var root = RootTag.find(bytes("<svg width=\"4\" height='2'></svg>"));
            assertEquals(
                    List.of("width", "height"),
                    root.attributes().stream().map(RootTag.Attribute::name).toList());
            assertEquals(Optional.of("2"), root.attribute("height"));
        }

        @Test
        @DisplayName("after a byte order mark, a declaration, comments, a doctype with a subset and white space")
        void afterAProlog() {
            var document = "\uFEFF<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n"
                    + "<!-- <svg width=\"99\"> in a comment is not the root -->\n"
                    + "<!DOCTYPE svg PUBLIC \"-//W3C//DTD SVG 1.1//EN\" \"x>y.dtd\" [\n"
                    + "  <!ENTITY e \"<svg>\">\n"
                    + "]>\n"
                    + "<?xml-stylesheet href=\"s.css\"?>\r\n\t"
                    + "<svg viewBox=\"0 0 10 5\"/>";
            var root = RootTag.find(bytes(document));
            assertEquals(Optional.of(new ViewBox(0, 0, 10, 5)), root.viewBox());
            assertEquals(Optional.empty(), root.attribute("width"));
        }

        @Test
        @DisplayName("with a prefixed attribute and a value holding the other quote and a >")
        void attributes() {
            var root = RootTag.find(bytes("<svg xmlns:xlink=\"http://www.w3.org/1999/xlink\" data-x='a \"b\" > c'>"));
            assertEquals(Optional.of("http://www.w3.org/1999/xlink"), root.attribute("xmlns:xlink"));
            assertEquals(Optional.of("a \"b\" > c"), root.attribute("data-x"));
        }

        @ParameterizedTest
        @ValueSource(
                strings = {
                    "",
                    "   ",
                    "<html><svg></svg></html>",
                    "<svgx width=\"1\">",
                    "<SVG>",
                    "<?xml version=\"1.0\"?><rect/>",
                    "<!-- never closed <svg>",
                    "<svg width=\"1\"",
                    "<svg width=1>",
                    "<svg width>",
                    "<svg width=\"1\"height=\"2\">",
                    "<svg width=\"1>",
                })
        @DisplayName("and refuses what has none, or one that does not close")
        void refuses(String document) {
            var thrown = assertThrows(ImageDecodeException.class, () -> RootTag.find(bytes(document)));
            assertTrue(thrown.getMessage().startsWith("not an SVG document"), thrown.getMessage());
        }
    }

    @Nested
    @DisplayName("reads")
    class Reading {

        @Test
        @DisplayName("preserveAspectRatio none, with and without defer, and the other values as placing")
        void stretches() {
            assertAll(
                    () -> assertTrue(root("preserveAspectRatio=\"none\"").stretches()),
                    () -> assertTrue(
                            root("preserveAspectRatio=\" defer none \"").stretches()),
                    () -> assertFalse(
                            root("preserveAspectRatio=\"xMidYMid slice\"").stretches()),
                    () -> assertFalse(root("preserveAspectRatio=\"xMinYMax\"").stretches()),
                    () -> assertFalse(root("").stretches()));
        }

        @Test
        @DisplayName("a color of the root's own, as an attribute or in its style")
        void namesItsColor() {
            assertAll(
                    () -> assertTrue(root("color=\"red\"").namesItsColor()),
                    () -> assertTrue(root("style=\"fill:none; color: red\"").namesItsColor()),
                    () -> assertTrue(root("style=\"COLOR:red\"").namesItsColor()),
                    () -> assertFalse(root("style=\"background-color:red\"").namesItsColor()),
                    () -> assertFalse(root("fill=\"red\"").namesItsColor()));
        }

        @Test
        @DisplayName("a view box only when it is four numbers with a size")
        void viewBox() {
            assertAll(
                    () -> assertEquals(
                            Optional.of(new ViewBox(-1, 2.5f, 3, 4)),
                            root("viewBox=\"-1,2.5 3, 4\"").viewBox()),
                    () -> assertEquals(
                            Optional.empty(), root("viewBox=\"0 0 0 4\"").viewBox()),
                    () -> assertEquals(
                            Optional.empty(), root("viewBox=\"0 0 -3 4\"").viewBox()),
                    () -> assertEquals(
                            Optional.empty(), root("viewBox=\"0 0 3\"").viewBox()),
                    () -> assertEquals(
                            Optional.empty(), root("viewBox=\"a b c d\"").viewBox()));
        }

        private RootTag root(String attributes) {
            return RootTag.find(bytes("<svg " + attributes + "></svg>"));
        }
    }

    @Nested
    @DisplayName("rewrites")
    class Rewriting {

        @Test
        @DisplayName("the named attributes at the end of the tag, the rest of the document untouched")
        void replaces() {
            var document = "<?xml version=\"1.0\"?><svg width=\"10\" viewBox=\"0 0 10 5\" height='5'>"
                    + "<rect width=\"10\" height=\"5\"/></svg>";
            var root = RootTag.find(bytes(document));
            var rewritten = text(root.with(RootTag.replacements("width", "40", "height", "20")));
            assertEquals(
                    "<?xml version=\"1.0\"?><svg viewBox=\"0 0 10 5\" width=\"40\" height=\"20\">"
                            + "<rect width=\"10\" height=\"5\"/></svg>",
                    rewritten);
            var again = RootTag.find(bytes(rewritten));
            assertEquals(Optional.of("40"), again.attribute("width"));
        }

        @Test
        @DisplayName("a self-closing root, which stays self-closing")
        void selfClosing() {
            var root = RootTag.find(bytes("<svg viewBox=\"0 0 1 1\" />"));
            assertEquals("<svg viewBox=\"0 0 1 1\" color=\"red\"/>", text(root.with("color", "red")));
        }

        @Test
        @DisplayName("nothing, and hands the same array back, when nothing is replaced")
        void nothing() {
            var document = bytes("<svg></svg>");
            assertSame(document, RootTag.find(document).with(RootTag.replacements()));
        }

        @Test
        @DisplayName("currentColor onto a root without a color, and not onto one with")
        void currentColor() {
            assertAll(
                    () -> assertEquals(
                            "<svg width=\"1\" color=\"rgba(18,52,86,1)\">",
                            text(RootTag.find(bytes("<svg width=\"1\">")).withCurrentColor(0xFF123456))),
                    () -> assertEquals(
                            "<svg color=\"blue\">",
                            text(RootTag.find(bytes("<svg color=\"blue\">")).withCurrentColor(0xFF123456))));
        }

        @Test
        @DisplayName("a colour as rgba() with commas and alpha from 0 to 1, which is what ThorVG reads")
        void cssColor() {
            assertAll(
                    () -> assertEquals("rgba(255,0,0,1)", RootTag.cssColor(0xFFFF0000)),
                    () -> assertEquals("rgba(0,0,255,0.5020)", RootTag.cssColor(0x800000FF)),
                    () -> assertEquals("rgba(1,2,3,0.0000)", RootTag.cssColor(0x00010203)));
        }

        @Test
        @DisplayName("and refuses a value that would end its own quotes")
        void refusesAQuote() {
            var root = RootTag.find(bytes("<svg>"));
            assertThrows(IllegalArgumentException.class, () -> root.with("color", "a\"b"));
            assertThrows(IllegalArgumentException.class, () -> RootTag.replacements("odd"));
        }
    }
}
