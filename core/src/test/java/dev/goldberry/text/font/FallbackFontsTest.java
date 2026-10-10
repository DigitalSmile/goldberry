package dev.goldberry.text.font;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import dev.goldberry.RendererRequirement;
import dev.goldberry.assets.BundledFont;
import dev.goldberry.text.Paragraph;

/// The book's side of fallback: which face it answers for a character, in what
/// order, at what weight, and what it does not open.
@DisplayName("a book with fallback faces")
class FallbackFontsTest {

    private static final BundledFont.Style UPRIGHT = BundledFont.Style.UPRIGHT;

    private Fonts fonts;

    @BeforeEach
    void requireRenderer() {
        RendererRequirement.enforce();
    }

    @AfterEach
    void closeBook() {
        if (fonts != null) {
            fonts.close();
        }
    }

    /// A stand-in that draws 中 with Inter's `drawnAs`.
    private static FontSource han(String family, int weight, char drawnAs) {
        return FontSource.of(family, weight, UPRIGHT, StandInFace.of(Map.of((int) '中', drawnAs), false, 2048));
    }

    /// The glyph `font` draws 中 with, through a paragraph and its fallbacks.
    private static int glyphForHan(Font font) {
        return Paragraph.of(font, "中").glyphs().glyphId(0);
    }

    private int inter(char character) {
        return StandInFace.glyphOf(fonts.of(BundledFont.UI, 14), character);
    }

    @Test
    @DisplayName("a book with none draws a character its face lacks as `.notdef`, as before")
    void none() {
        fonts = Fonts.bundled();
        var font = fonts.of(BundledFont.UI, 14);

        assertTrue(fonts.fallbacks().isEmpty(), "no artifact in :core's tests provides any");
        assertTrue(font.fallbacks().isEmpty());
        assertEquals(0, glyphForHan(font));
    }

    @Test
    @DisplayName("the first fallback that has the character draws it, at the size of the font it stands in for")
    void order() {
        fonts = Fonts.bundled(
                List.of(),
                List.of(FallbackSource.of(han("First", 400, 'A')), FallbackSource.of(han("Second", 400, 'B'))));
        var font = fonts.of(BundledFont.UI, 20);

        var fallback = font.fallbacks().fontFor("中", 0, 1);
        assertNotNull(fallback);
        assertEquals("First", fallback.face().name());
        assertEquals(20, fallback.size());
        assertEquals(inter('A'), glyphForHan(font));
        assertSame(fallback, font.fallbacks().fontFor("中", 0, 1), "one font per face and size, kept by the book");
        assertNull(font.fallbacks().fontFor("☃", 0, 1));
    }

    @Test
    @DisplayName("a fallback whose scripts are named is not opened for a character of another script")
    void hint() {
        fonts = Fonts.bundled(
                List.of(),
                List.of(
                        FallbackSource.of(han("Arabic only", 400, 'A'), Character.UnicodeScript.ARABIC),
                        FallbackSource.of(han("Han", 400, 'B'), Character.UnicodeScript.HAN)));
        var font = fonts.of(BundledFont.UI, 14);
        assertEquals(1, fonts.openFaces());

        assertEquals(inter('B'), glyphForHan(font));
        // Inter and the Han face: the first fallback has 中 in its cmap, and the
        // hint kept it shut.
        assertEquals(2, fonts.openFaces());
    }

    @Test
    @DisplayName("text the face covers never opens a fallback")
    void coveredOpensNothing() {
        fonts = Fonts.bundled(List.of(), List.of(FallbackSource.of(han("Han", 400, 'A'))));
        var font = fonts.of(BundledFont.UI, 14);

        Paragraph.of(font, "Hello, world. Grüße, Жанна.");
        assertEquals(1, fonts.openFaces());
    }

    @Test
    @DisplayName("bold text takes the family's nearest weight, and a family with one weight gives that one")
    void weights() {
        fonts = Fonts.bundled(
                List.of(),
                List.of(
                        FallbackSource.of(han("Han", 400, 'A')),
                        FallbackSource.of(han("Han", 700, 'B')),
                        FallbackSource.of(han("Other", 400, 'C'))));

        assertEquals(inter('A'), glyphForHan(fonts.of(BundledFont.UI, 14)));
        // Inter's semi-bold is 600, and above 500 CSS looks heavier first.
        assertEquals(inter('B'), glyphForHan(fonts.of(BundledFont.UI_STRONG, 14)));
    }

    @Test
    @DisplayName("a fallback whose file is not there is skipped, and the character is `.notdef`")
    void unreadable() {
        var missing = FontSource.stream("Missing", 400, UPRIGHT, () -> null);
        fonts = Fonts.bundled(List.of(), List.of(FallbackSource.of(missing)));
        var font = fonts.of(BundledFont.UI, 14);

        assertEquals(0, glyphForHan(font));
        assertEquals(1, fonts.openFaces());
    }

    @Test
    @DisplayName("fonts opened by hand can be given fallbacks by hand")
    void byHand() {
        try (var face = FontFace.bundled(BundledFont.UI);
                var font = Font.on(face, 14);
                var fallback = Font.of(StandInFace.of(StandInFace.scripts(), false, 2048), 14)) {
            assertEquals(0, glyphForHan(font));
            font.fallbacks(Fallbacks.of(List.of(fallback)));
            assertSame(fallback, font.fallbacks().fontFor("中文", 0, 1));
            assertTrue(glyphForHan(font) != 0);
            assertSame(Fallbacks.NONE, Fallbacks.of(List.of()));
        }
    }
}
