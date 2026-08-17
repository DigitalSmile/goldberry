package io.github.digitalsmile.goldberry.text;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.digitalsmile.goldberry.RendererRequirement;
import io.github.digitalsmile.goldberry.assets.BundledFont;
import io.github.digitalsmile.goldberry.css.Typography;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/// The book that joins a resolved [Typography] to a [Font].
///
/// Its whole reason for existing is the frame loop: a widget tree is rendered
/// from scratch every frame, so without a cache `font-size: 20px` on one heading
/// would re-parse Inter — 681 µs and a second copy of a megabyte and a half —
/// sixty times a second.
class FontsTest {

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

    @Nested
    @DisplayName("caching")
    class Caching {

        @Test
        @DisplayName("nothing is opened until something is asked for")
        void lazy() {
            // An application that never draws code text never pays for JetBrains
            // Mono, which matters on the start-up path §1 makes claims about.
            assertEquals(0, fonts.openFaces());
            assertEquals(0, fonts.openFonts());
        }

        @Test
        @DisplayName("the same face and size is the same font")
        void sameFont() {
            assertSame(fonts.of(BundledFont.UI, 13), fonts.of(BundledFont.UI, 13));
            assertEquals(1, fonts.openFonts());
        }

        @Test
        @DisplayName("two sizes share one face")
        void sharedFace() {
            // ADR-0044's whole point: a face is size-independent because the
            // shaper is never scaled, so a second size costs 4.4 microseconds
            // rather than 681 and no second copy of the file.
            fonts.of(BundledFont.UI, 13);
            fonts.of(BundledFont.UI, 20);

            assertEquals(2, fonts.openFonts());
            assertEquals(1, fonts.openFaces());
        }

        @Test
        @DisplayName("two weights are two faces, because a weight is a face here")
        void weightIsAFace() {
            fonts.of(BundledFont.UI, 13);
            fonts.of(BundledFont.UI_STRONG, 13);

            assertEquals(2, fonts.openFaces());
        }

        @Test
        @DisplayName("floating-point noise does not open a second font")
        void quantized() {
            // Two `13.000000000000002`s from different `em` chains are the same
            // font to any reader. A cache that disagreed would open one face per
            // frame and look exactly like a leak.
            assertSame(fonts.of(BundledFont.UI, 13), fonts.of(BundledFont.UI, 13.0000000001));
            assertEquals(1, fonts.openFonts());

            assertNotSame(fonts.of(BundledFont.UI, 13), fonts.of(BundledFont.UI, 13.5));
        }

        @Test
        @DisplayName("a size that is not a size is refused")
        void badSize() {
            assertThrows(IllegalArgumentException.class, () -> fonts.of(BundledFont.UI, 0));
            assertThrows(IllegalArgumentException.class, () -> fonts.of(BundledFont.UI, -3));
            assertThrows(IllegalArgumentException.class, () -> fonts.of(BundledFont.UI, Double.NaN));
        }
    }

    @Nested
    @DisplayName("resolving a style")
    class Resolving {

        @Test
        @DisplayName("the family and weight pick the face, the size picks the font")
        void fromTypography() {
            var body = fonts.of(Typography.INITIAL);
            var strong = fonts.of(Typography.INITIAL.weight(BundledFont.Weight.SEMI_BOLD));

            assertEquals(13, body.size(), 1e-9, "§1.4's body size");
            assertNotSame(body, strong, "600 is a different face, so a different font");
            assertEquals(2, fonts.openFaces());
        }

        @Test
        @DisplayName("a family nobody bundled falls back to the UI face")
        void unknownFamily() {
            // §6.1 has no fallback *cascade* -- a missing glyph is .notdef on
            // purpose -- but a missing *family* is a stylesheet naming a font
            // that was never shipped, and drawing that in Inter beats a window
            // with no text in it.
            var style = Typography.INITIAL.family("Comic Sans MS");

            assertSame(fonts.of(BundledFont.UI, 13), fonts.of(style));
        }

        @Test
        @DisplayName("a family with no strong face falls back to its own regular")
        void noStrongFace() {
            // `mono` is specified at 400 only. Refusing would mean bold code text
            // throwing from inside a paint pass.
            assertSame(
                    BundledFont.CODE,
                    BundledFont.of("JetBrains Mono", BundledFont.Weight.SEMI_BOLD));
        }
    }

    @Nested
    @DisplayName("lifetime")
    class Lifetime {

        @Test
        @DisplayName("closing closes every font and face")
        void closes() {
            var font = fonts.of(BundledFont.UI, 13);
            var face = fonts.faceOf(BundledFont.UI);

            fonts.close();

            assertTrue(fonts.isClosed());
            assertTrue(font.isClosed());
            assertTrue(face.isClosed());
            assertEquals(0, fonts.openFonts());
        }

        @Test
        @DisplayName("closing twice is harmless")
        void idempotent() {
            fonts.close();
            fonts.close();
        }

        @Test
        @DisplayName("a closed book refuses to open anything else")
        void refusesAfterClose() {
            fonts.close();
            assertThrows(IllegalStateException.class, () -> fonts.of(BundledFont.UI, 13));
        }

        @Test
        @DisplayName("every font and face is released, across sizes and weights")
        void releasesEverything() {
            // Blend2D and HarfBuzz both keep references from a font into its
            // face, so closing the face first would leave them reading unmapped
            // memory. What is observable here is that all of them end up closed
            // and the process survives it; the *order* is what the implementation
            // states and what this would crash on if it were wrong.
            var small = fonts.of(BundledFont.UI, 13);
            var large = fonts.of(BundledFont.UI, 20);
            var strong = fonts.of(BundledFont.UI_STRONG, 13);
            var uiFace = fonts.faceOf(BundledFont.UI);
            var strongFace = fonts.faceOf(BundledFont.UI_STRONG);

            fonts.close();

            for (var font : List.of(small, large, strong)) {
                assertTrue(font.isClosed(), font + " survived the book");
            }
            assertTrue(uiFace.isClosed());
            assertTrue(strongFace.isClosed());
            assertEquals(0, fonts.openFaces());
        }
    }
}
