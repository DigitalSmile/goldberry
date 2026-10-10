package dev.goldberry.text;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import dev.goldberry.RendererRequirement;
import dev.goldberry.assets.BundledFont;
import dev.goldberry.golden.GoldenImage;
import dev.goldberry.text.font.FallbackSource;
import dev.goldberry.text.font.Font;
import dev.goldberry.text.font.FontSource;
import dev.goldberry.text.font.Fonts;
import dev.goldberry.text.font.StandInFace;

/// A paragraph with characters its face has no glyph for, and a book with a
/// face that has them.
///
/// The fallback is a stand-in: Inter's outlines behind a `cmap` claiming Han,
/// Arabic and mathematical Fraktur, on a grid of 1024 to the em where Inter's is
/// 2048, so every glyph it draws is twice the width of the Inter letter it
/// borrows and a width that forgot to rescale is off by half.
@DisplayName("a paragraph with fallback faces")
class ParagraphFallbackTest {

    private static final double SIZE = 14;

    private Fonts fonts;
    private Fonts plain;
    private Font font;
    private Font tofu;

    @BeforeEach
    void openBooks() {
        RendererRequirement.enforce();
        var standIn = FontSource.of(
                "Stand In", 400, BundledFont.Style.UPRIGHT, StandInFace.of(StandInFace.scripts(), true, 1024));
        fonts = Fonts.bundled(List.of(), List.of(FallbackSource.of(standIn)));
        plain = Fonts.bundled();
        font = fonts.of(BundledFont.UI, SIZE);
        tofu = plain.of(BundledFont.UI, SIZE);
    }

    @AfterEach
    void closeBooks() {
        if (fonts != null) {
            fonts.close();
        }
        if (plain != null) {
            plain.close();
        }
    }

    private static void assertNoNotdef(Paragraph paragraph) {
        var glyphs = paragraph.glyphs();
        for (var i = 0; i < glyphs.length(); i++) {
            var at = i;
            assertNotEquals(0, glyphs.glyphId(i), () -> "glyph " + at + " of \"" + paragraph.text() + "\" is .notdef");
        }
    }

    @Test
    @DisplayName("a name in Han is drawn by the fallback, and the words before it by the font")
    void han() {
        var text = "Ann " + StandInFace.HAN;
        var paragraph = Paragraph.of(font, text);

        assertNoNotdef(paragraph);
        for (var i = 0; i < 4; i++) {
            assertSame(font, paragraph.shapedIn(i));
        }
        var fallback = paragraph.shapedIn(4);
        assertNotSame(font, fallback);
        assertEquals("Stand In", fallback.face().name());
        assertEquals(SIZE, fallback.size());
        for (var i = 4; i < text.length(); i++) {
            assertSame(fallback, paragraph.shapedIn(i));
        }
    }

    @Test
    @DisplayName("without a fallback the same name is `.notdef`, exactly as before")
    void withoutFallback() {
        var paragraph = Paragraph.of(tofu, "Ann " + StandInFace.HAN);
        var glyphs = paragraph.glyphs();
        assertEquals(8, glyphs.length());
        for (var i = 4; i < 8; i++) {
            assertEquals(0, glyphs.glyphId(i));
        }
        for (var i = 0; i < 8; i++) {
            assertSame(tofu, paragraph.shapedIn(i));
        }
    }

    @Test
    @DisplayName("text the face covers is shaped exactly as it is with no fallback at all")
    void coveredIsUntouched() {
        var text = "Hello, world. Grüße, Жанна\n— and a second line.";
        var with = Paragraph.of(font, text);
        var without = Paragraph.of(tofu, text);

        assertEquals(without.glyphs().length(), with.glyphs().length());
        for (var i = 0; i < with.glyphs().length(); i++) {
            assertEquals(without.glyphs().glyphId(i), with.glyphs().glyphId(i));
            assertEquals(without.glyphs().xAdvance(i), with.glyphs().xAdvance(i));
            assertEquals(without.glyphs().cluster(i), with.glyphs().cluster(i));
        }
        for (var i = 0; i < text.length(); i++) {
            assertSame(font, with.shapedIn(i));
        }
        assertEquals(without.layout(120).lines(), with.layout(120).lines());
        assertEquals(1, fonts.openFaces(), "the fallback face was never opened");
    }

    @Test
    @DisplayName("mathematical letters above the BMP are routed whole, surrogate pairs and all")
    void fraktur() {
        var paragraph = Paragraph.of(font, StandInFace.FRAKTUR);
        assertNoNotdef(paragraph);
        assertEquals(5, paragraph.glyphs().length());
        assertNotSame(font, paragraph.shapedIn(0));
    }

    @Test
    @DisplayName("a mark goes with its letter: the cluster is never split between two faces")
    void clusters() {
        // Inter has `e` and not the fatha, and the stand-in has both: the whole
        // cluster goes to the stand-in rather than leaving the mark as a box.
        var latin = Paragraph.of(font, "xe" + StandInFace.FATHA);
        assertSame(font, latin.shapedIn(0));
        assertNotSame(font, latin.shapedIn(1));
        assertSame(latin.shapedIn(1), latin.shapedIn(2));
        assertNoNotdef(latin);

        // The stand-in has 中 and not U+0302, which Inter has. Nobody has both,
        // so the cluster goes where its letter is, mark and all.
        var han = Paragraph.of(font, "中\u0302");
        assertSame(han.shapedIn(0), han.shapedIn(1));
        assertNotSame(font, han.shapedIn(0));

        // And no offset inside a cluster is a place a caret can land.
        assertEquals(3, latin.offsetAt(0, 3, latin.widthBetween(0, 3) + 1));
        assertEquals(1, latin.offsetAt(0, 3, latin.widthBetween(0, 1) + 0.01));
    }

    @Test
    @DisplayName("an Arabic name is one run in one face, in logical order, beside the bidi approximation")
    void arabic() {
        var text = "Ann " + StandInFace.ARABIC;
        var paragraph = Paragraph.of(font, text);

        assertTrue(paragraph.isBidiApproximate());
        assertNoNotdef(paragraph);
        var fallback = paragraph.shapedIn(4);
        assertNotSame(font, fallback);
        // The space between the two words too: the name is shaped as one run,
        // so it joins as a word would.
        for (var i = 4; i < text.length(); i++) {
            assertSame(fallback, paragraph.shapedIn(i), "offset " + i);
        }
        // Logical order, which is what every measurement here assumes: the
        // approximation the paragraph already makes, unchanged by the face.
        var glyphs = paragraph.glyphs();
        for (var i = 1; i < glyphs.length(); i++) {
            assertTrue(glyphs.cluster(i) >= glyphs.cluster(i - 1), "cluster order at glyph " + i);
        }
    }

    @Test
    @DisplayName("widths are rescaled into the font's units, so the caret sits where the glyph ends")
    void carets() {
        var text = "Ann " + StandInFace.HAN;
        var paragraph = Paragraph.of(font, text);

        // 中 borrows Inter's Z on a grid half the size: twice Z's width.
        var z = tofu.widthOf("Z");
        assertEquals(2 * z, paragraph.widthBetween(4, 5), 1e-3);
        assertEquals(tofu.widthOf("Ann "), paragraph.widthBetween(0, 4), 1e-3);

        var previous = -1.0;
        for (var offset = 0; offset <= text.length(); offset++) {
            var x = paragraph.widthBetween(0, offset);
            assertTrue(x > previous, "the caret moves right at offset " + offset);
            previous = x;
            assertEquals(offset, paragraph.offsetAt(0, text.length(), x), "a click on the caret at " + offset);
        }
        assertEquals(
                paragraph.widthBetween(0, text.length()),
                paragraph.layout(Paragraph.UNCONSTRAINED).width());
    }

    @Test
    @DisplayName("the line box is the font's own, whatever the fallback's metrics")
    void lineBox() {
        var paragraph = Paragraph.of(font, StandInFace.HAN);
        assertEquals(
                font.lineHeight(), paragraph.layout(Paragraph.UNCONSTRAINED).height());
        assertEquals(
                font.ascent(),
                paragraph.ascentOf(
                        paragraph.layout(Paragraph.UNCONSTRAINED).lines().getFirst()));
    }

    /// The first line has no fallback and draws boxes; the second has the
    /// stand-in, on Inter's own grid here so the borrowed letters sit at Inter's
    /// size, and draws a letter for every character. What the picture pins is
    /// the routing and where each segment's pen lands, not what Han looks like.
    @Test
    @DisplayName("a picture: one name in each script, with no fallback and with one")
    void picture() {
        var standIn = FontSource.of(
                "Stand In", 400, BundledFont.Style.UPRIGHT, StandInFace.of(StandInFace.scripts(), true, 2048));
        try (var book = Fonts.bundled(List.of(), List.of(FallbackSource.of(standIn)))) {
            var names = "Anna · " + StandInFace.HAN + " · " + StandInFace.ARABIC + " · " + StandInFace.FRAKTUR;
            var without = Paragraph.of(tofu, names);
            var with = Paragraph.of(book.of(BundledFont.UI, SIZE), names);
            GoldenImage.assertMatches("fallback-names", 300, 52, 1.0f, frame -> {
                frame.fillRect(0, 0, 300, 52, 0xFFECEFF4);
                without.paint(frame, 8, 6, 284, 0xFF2E3440);
                with.paint(frame, 8, 28, 284, 0xFF2E3440);
            });
        }
    }
}
