package dev.goldberry.css;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import dev.goldberry.RendererRequirement;
import dev.goldberry.assets.BundledFont;
import dev.goldberry.css.cascade.CascadeLayer;
import dev.goldberry.css.parse.CssParser;
import dev.goldberry.css.parse.CssSyntaxException;
import dev.goldberry.css.parse.ParseMode;
import dev.goldberry.text.font.Fonts;
import dev.goldberry.widget.WidgetRenderer;

/// `@font-face`: read by the parser, registered by a renderer, drawn by
/// `font-family`.
class FontFaceTest {

    /// The toolkit's own monospaced face, as a stylesheet would name a file.
    private static final String MONO = "classpath:/dev/goldberry/assets/fonts/JetBrainsMono.ttf";

    @Nested
    @DisplayName("read")
    class Read {

        private FontFaceRule only(String css) {
            var faces = CssParser.parseSheet(css, ParseMode.STRICT, "t").fontFaces();
            assertEquals(1, faces.size());
            return faces.getFirst();
        }

        @Test
        @DisplayName("family, the first source it can read, weight and style")
        void descriptors() {
            var face = only("""
                    @font-face {
                      font-family: "Atkinson Hyperlegible", sans-serif;
                      src: local("Atkinson"), url(a.woff2) format("woff2"), url("a.ttf") format("truetype");
                      font-weight: bold;
                      font-style: italic;
                      unicode-range: U+0000-00FF;
                    }
                    """);
            assertEquals(new FontFaceRule("Atkinson Hyperlegible", 700, BundledFont.Style.ITALIC, "a.ttf", 1, 1), face);
        }

        @Test
        @DisplayName("a weight range takes its first number")
        void range() {
            assertEquals(
                    100,
                    only("@font-face { font-family: V; src: url(v.ttf); font-weight: 100 900 }")
                            .weight());
        }

        @ParameterizedTest(name = "{0}")
        @ValueSource(
                strings = {
                    "@font-face { font-family: X; src: url(x.woff2) format(\"woff2\") }",
                    "@font-face { font-family: X; src: local(X) }",
                    "@font-face { font-family: X; src: url(x.ttf); font-style: oblique }",
                    "@media (min-width: 1px) { @font-face { font-family: X; src: url(x.ttf) } }",
                })
        @DisplayName("refuses a face it cannot draw")
        void refuses(String css) {
            var thrown = assertThrows(CssSyntaxException.class, () -> CssParser.parseSheet(css, ParseMode.STRICT, "t"));
            assertTrue(thrown.isUnsupportedFeature(), thrown::getMessage);
            assertEquals(
                    1,
                    CssParser.parseSheet(css, ParseMode.LENIENT, "t").dropped().size());
        }

        @Test
        @DisplayName("a face with no family is malformed")
        void noFamily() {
            assertThrows(CssSyntaxException.class, () -> CssParser.parseSheet("@font-face { src: url(x.ttf) }"));
        }

        @Test
        @DisplayName("a resource sheet's src is resolved beside it")
        void anchored() {
            var sheet = Stylesheet.resource(CascadeLayer.APPLICATION, FontFaceTest.class, "fontface/faces.css");
            assertEquals(
                    "classpath:/dev/goldberry/css/fontface/fonts/brand.ttf",
                    sheet.fontFaces().getFirst().src());
        }
    }

    @Nested
    @DisplayName("drawn")
    class Drawn {

        private Fonts fonts;

        @BeforeEach
        void setUp() {
            RendererRequirement.enforce();
            fonts = Fonts.bundled();
        }

        @AfterEach
        void tearDown() {
            if (fonts != null) {
                fonts.close();
            }
        }

        @Test
        @DisplayName("a renderer registers the faces its sheets ship, once")
        void registers() {
            var sheet = Stylesheet.parse(
                    CascadeLayer.APPLICATION, "@font-face { font-family: \"Sheet Mono\"; src: url(\"" + MONO + "\") }");
            assertTrue(fonts.shipped().isEmpty());
            new WidgetRenderer(List.of(sheet), fonts);
            new WidgetRenderer(List.of(sheet), fonts);
            assertEquals(1, fonts.shipped().size());
            assertEquals("Sheet Mono", fonts.shipped().getFirst().family());
            assertTrue(fonts.unreadable().isEmpty(), "the file is there");
        }

        @Test
        @DisplayName("font-family then draws text in the face")
        void drawsInTheFace() {
            var sheet = Stylesheet.parse(
                    CascadeLayer.APPLICATION, "@font-face { font-family: \"Sheet Mono\"; src: url(\"" + MONO + "\") }");
            new WidgetRenderer(List.of(sheet), fonts);
            var mono = fonts.of(new Typography("Sheet Mono", 20, 400, 24));
            var inter = fonts.of(new Typography("Inter", 20, 400, 24));
            assertEquals(mono.widthOf("i"), mono.widthOf("m"), 1e-6, "a monospaced face");
            assertNotEquals(inter.widthOf("i"), inter.widthOf("m"), "unlike the UI face");
        }

        @Test
        @DisplayName("a face whose file is not there is kept and reported, and drawn in the UI face")
        void missingFile() {
            var sheet = Stylesheet.parse(
                    CascadeLayer.APPLICATION, "@font-face { font-family: Gone; src: url(\"classpath:/no/such.ttf\") }");
            new WidgetRenderer(List.of(sheet), fonts);
            assertEquals(1, fonts.unreadable().size());
            assertFalse(fonts.shipped().isEmpty());
        }
    }
}
