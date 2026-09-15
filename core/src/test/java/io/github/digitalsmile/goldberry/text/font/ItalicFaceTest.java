package io.github.digitalsmile.goldberry.text.font;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import io.github.digitalsmile.goldberry.RendererRequirement;
import io.github.digitalsmile.goldberry.assets.BundledFont;
import io.github.digitalsmile.goldberry.css.Typography;
import io.github.digitalsmile.goldberry.paint.TestFrames;
import io.github.digitalsmile.goldberry.text.Paragraph;

/// The italic faces, in ink — `docs/gaps.md` G27, ADR-0323.
///
/// The claim being tested is the one an application could not make for itself:
/// **an italic here is a drawn face and not a sheared upright one.** Nothing about
/// a plumbing test would say that, so every assertion below is about pixels or
/// about the glyphs that produced them.
class ItalicFaceTest {

    private static final int BACKGROUND = 0xFF000000;
    private static final int INK = 0xFFFFFFFF;

    private static final int WIDTH = 320;
    private static final int HEIGHT = 80;

    /// Letters whose italic forms differ from their uprights in Inter — `a` is
    /// single-storey in the italic, `f` gains a descending tail.
    private static final String TEXT = "affable";

    @BeforeEach
    void requireRenderer() {
        RendererRequirement.enforce();
    }

    @Test
    @DisplayName("the italic face draws different pixels from the upright one")
    void italicIsNotTheUpright() {
        var upright = paint(BundledFont.UI);
        var italic = paint(BundledFont.UI_ITALIC);

        var differing = differingPixels(upright, italic);
        assertTrue(
                differing > 200,
                () -> "only " + differing + " pixels differ, which is not a different set of letterforms");
    }

    @Test
    @DisplayName("and the semibold italic differs from the regular italic")
    void weightStillShowsInTheItalic() {
        var regular = paint(BundledFont.UI_ITALIC);
        var strong = paint(BundledFont.UI_STRONG_ITALIC);

        assertTrue(
                differingPixels(regular, strong) > 100,
                "the fourth corner of the matrix is drawing the third one's file");
    }

    /// A shear would leave the advances alone — it is a transform of the outlines,
    /// not of the metrics. A drawn italic has its own.
    @Test
    @DisplayName("the italic has metrics of its own, not the upright's")
    void metricsAreTheFacesOwn() {
        try (var upright = Font.bundled(BundledFont.UI, 32);
                var italic = Font.bundled(BundledFont.UI_ITALIC, 32)) {

            assertNotEquals(
                    upright.widthOf(TEXT),
                    italic.widthOf(TEXT),
                    "identical advances would mean one file is being drawn twice");
            assertTrue(italic.ascent() > 0 && italic.descent() > 0, "the face reports usable metrics");
            assertTrue(
                    italic.decorations().underlineThickness() > 0,
                    "and an italic carries its own underline, which is drawn at its own angle-free position");
        }
    }

    /// The cascade's half: a resolved style names the face, so `font-style: italic`
    /// on a heading reaches the file without the painter knowing anything about it.
    @Test
    @DisplayName("a resolved typography picks the face out of the matrix")
    void typographyResolvesTheFace() {
        var body = Typography.INITIAL;

        assertEquals(BundledFont.UI, body.face());
        assertEquals(BundledFont.UI_ITALIC, body.style(BundledFont.Style.ITALIC).face());
        assertEquals(
                BundledFont.UI_STRONG_ITALIC,
                body.weight(BundledFont.Weight.SEMI_BOLD)
                        .style(BundledFont.Style.ITALIC)
                        .face());
        assertEquals(
                BundledFont.UI_STRONG,
                body.weight(BundledFont.Weight.SEMI_BOLD)
                        .style(BundledFont.Style.ITALIC)
                        .style(BundledFont.Style.UPRIGHT)
                        .face(),
                "and going back is going back");
    }

    /// Four faces of one family are four entries in the book, opened on demand:
    /// an application that never writes `font-style: italic` never parses one.
    @Test
    @DisplayName("the font book opens an italic face only when something asks")
    void facesAreOpenedLazily() {
        try (var fonts = Fonts.bundled()) {
            assertEquals(0, fonts.openFaces());

            fonts.of(Typography.INITIAL);
            assertEquals(1, fonts.openFaces());

            fonts.of(Typography.INITIAL.style(BundledFont.Style.ITALIC));
            assertEquals(2, fonts.openFaces(), "the italic is a second face rather than a setting on the first");

            // Asking again is free, which is what makes a frame that draws italic
            // text cost nothing extra after the first one.
            fonts.of(Typography.INITIAL.style(BundledFont.Style.ITALIC));
            assertEquals(2, fonts.openFaces());
        }
    }

    // --- helpers --------------------------------------------------------------

    private TestFrames.Target paint(BundledFont face) {
        var target = TestFrames.of(WIDTH, HEIGHT, 1.0f);
        target.frame().fill(BACKGROUND);
        try (var font = Font.bundled(face, 32)) {
            Paragraph.of(font, TEXT).paint(target.frame(), 4, 4, WIDTH - 8, INK);
        }
        target.end();
        return target;
    }

    private static int differingPixels(TestFrames.Target one, TestFrames.Target other) {
        var count = 0;
        for (var y = 0; y < HEIGHT; y++) {
            for (var x = 0; x < WIDTH; x++) {
                if (one.pixel(x, y) != other.pixel(x, y)) {
                    count++;
                }
            }
        }
        return count;
    }
}
