package dev.goldberry.text;

import static dev.goldberry.css.TestElement.element;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import dev.goldberry.RendererRequirement;
import dev.goldberry.assets.BundledFont;
import dev.goldberry.css.ComputedStyle;
import dev.goldberry.css.Lettering;
import dev.goldberry.css.Stylesheet;
import dev.goldberry.css.cascade.CascadeLayer;
import dev.goldberry.css.cascade.StyleResolver;
import dev.goldberry.css.value.CssLength;
import dev.goldberry.text.font.Font;

/// `letter-spacing` and `word-spacing`: what the cascade computes, and what a
/// paragraph shaped with it measures.
class SpacingTest {

    @Nested
    @DisplayName("in the cascade")
    class Cascade {

        /// A `text` under a `card` whose font size is 20px.
        private ComputedStyle child(String css) {
            var sheet = Stylesheet.parse(CascadeLayer.APPLICATION, "card { font-size: 20px } " + css);
            var root = element("card");
            root.with(element("text"));
            var resolver = new StyleResolver(List.of(sheet));
            var context = new CssLength.Context(16, 16);
            var parent = ComputedStyle.of(resolver.resolve(root), context, null);
            return ComputedStyle.of(resolver.resolve(root.descend(1)), context, parent);
        }

        @Test
        @DisplayName("a length, em against the element's own size, and normal as nothing")
        void lengths() {
            assertEquals(
                    2,
                    child("text { letter-spacing: 2px }")
                            .typography()
                            .lettering()
                            .letterSpacing());
            assertEquals(
                    2,
                    child("text { letter-spacing: 0.1em }")
                            .typography()
                            .lettering()
                            .letterSpacing(),
                    1e-6);
            assertEquals(
                    -1,
                    child("text { word-spacing: -1px }")
                            .typography()
                            .lettering()
                            .wordSpacing());
            assertEquals(
                    Lettering.NONE,
                    child("text { letter-spacing: normal }").typography().lettering());
        }

        @Test
        @DisplayName("inherits the pixels the parent computed")
        void inherits() {
            var style = child("card { letter-spacing: 0.1em } text { font-size: 10px }");
            assertEquals(2, style.typography().lettering().letterSpacing(), 1e-6);
        }

        @Test
        @DisplayName("a percentage is dropped, and the font shorthand leaves the spacing alone")
        void refusals() {
            assertEquals(
                    Lettering.NONE,
                    child("text { word-spacing: 50% }").typography().lettering());
            var style = child("text { letter-spacing: 3px; font: 12px Inter }");
            assertEquals(3, style.typography().lettering().letterSpacing());
        }

        @Test
        @DisplayName("scales with the text-scale token")
        void scales() {
            var scaled = child("text { letter-spacing: 2px }").typography().scaled(1.5);
            assertEquals(3, scaled.lettering().letterSpacing(), 1e-9);
        }
    }

    @Nested
    @DisplayName("in a paragraph")
    class Shaped {

        private Font font;

        @BeforeEach
        void setUp() {
            RendererRequirement.enforce();
            font = Font.bundled(BundledFont.UI, 20);
        }

        @AfterEach
        void tearDown() {
            if (font != null) {
                font.close();
            }
        }

        private double width(String text, double letter, double word) {
            return Paragraph.of(font, text, letter, word)
                    .layout(Paragraph.UNCONSTRAINED)
                    .width();
        }

        @Test
        @DisplayName("letter spacing is added after every character")
        void letters() {
            assertEquals(width("abcd", 0, 0) + 8, width("abcd", 2, 0), 0.05);
        }

        @Test
        @DisplayName("word spacing is added to every space, and nowhere else")
        void words() {
            assertEquals(width("a b c", 0, 0) + 10, width("a b c", 0, 5), 0.05);
            assertEquals(width("abc", 0, 0), width("abc", 0, 5), 0.05);
        }

        @Test
        @DisplayName("negative spacing tightens")
        void tightens() {
            assertEquals(width("abcd", 0, 0) - 4, width("abcd", -1, 0), 0.05);
        }

        @Test
        @DisplayName("the paragraph wraps with the spacing it is drawn with")
        void wraps() {
            var plain = Paragraph.of(font, "aaaa aaaa aaaa", 0, 0);
            var wide = plain.layout(Paragraph.UNCONSTRAINED).width();
            assertEquals(1, plain.layout(wide + 1).lines().size());
            var spaced = Paragraph.of(font, "aaaa aaaa aaaa", 1, 0);
            assertTrue(spaced.layout(wide + 1).lines().size() > 1, "the spacing pushes the last word down");
        }

        @Test
        @DisplayName("the cache keeps a paragraph per spacing")
        void cached() {
            var cache = ParagraphCache.create();
            var plain = cache.paragraph(font, "abc");
            assertSame(plain, cache.paragraph(font, "abc", 0, 0));
            var spaced = cache.paragraph(font, "abc", 1, 0);
            assertNotSame(plain, spaced);
            assertSame(spaced, cache.paragraph(font, "abc", 1, 0));
        }

        @Test
        @DisplayName("refuses a spacing that is not a length")
        void refuses() {
            assertThrows(IllegalArgumentException.class, () -> Paragraph.of(font, "a", Double.NaN, 0));
        }
    }
}
