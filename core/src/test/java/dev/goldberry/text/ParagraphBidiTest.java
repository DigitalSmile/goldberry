package dev.goldberry.text;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashSet;
import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import dev.goldberry.RendererRequirement;
import dev.goldberry.assets.BundledFont;
import dev.goldberry.image.Image;
import dev.goldberry.layout.Direction;
import dev.goldberry.offscreen.Offscreen;
import dev.goldberry.paint.Frame;
import dev.goldberry.text.bidi.Directionality;
import dev.goldberry.text.flow.TextAlign;
import dev.goldberry.text.flow.TextFlow;
import dev.goldberry.text.flow.TextOverflow;
import dev.goldberry.text.flow.WhiteSpace;
import dev.goldberry.text.font.FallbackSource;
import dev.goldberry.text.font.Font;
import dev.goldberry.text.font.FontSource;
import dev.goldberry.text.font.Fonts;
import dev.goldberry.text.font.StandInFace;

/// A paragraph split into directional runs: shaped each its own way, placed in
/// visual order, and measured, clicked and selected in that order.
///
/// The Arabic is drawn by a stand-in face, Inter's outlines behind a `cmap`
/// that maps each Arabic letter to a Latin one (م is `m`, ح is `h`, د is
/// `d`), on Inter's own grid. A right-to-left word is then visible as its Latin
/// letters reversed, and its widths are Inter's.
@DisplayName("a paragraph with text in both directions")
class ParagraphBidiTest {

    private static final double SIZE = 14;

    /// Muhammad: م ح م د, drawn as `m h m d`.
    private static final String MUHAMMAD = "محمد";

    private Fonts fonts;
    private Fonts plain;
    private Font font;
    private Font latin;

    @BeforeEach
    void openBooks() {
        RendererRequirement.enforce();
        var standIn = FontSource.of(
                "Stand In", 400, BundledFont.Style.UPRIGHT, StandInFace.of(StandInFace.scripts(), true, 2048));
        fonts = Fonts.bundled(List.of(), List.of(FallbackSource.of(standIn)));
        plain = Fonts.bundled();
        font = fonts.of(BundledFont.UI, SIZE);
        latin = plain.of(BundledFont.UI, SIZE);
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

    private static Image draw(int width, int height, java.util.function.Consumer<Frame> scene) {
        return Offscreen.of(width, height).paint((frame, logical) -> {
            frame.fillRect(0, 0, width, height, 0xFFFFFFFF);
            scene.accept(frame);
        });
    }

    /// The columns of `image` with any ink in `[top, bottom)`.
    private static boolean inked(Image image, int x, int top, int bottom) {
        for (var y = top; y < bottom; y++) {
            if (image.argb(x, y) != 0xFFFFFFFF) {
                return true;
            }
        }
        return false;
    }

    @Test
    @DisplayName("a right-to-left word is drawn as its letters reversed")
    void drawnReversed() {
        var arabic = Paragraph.of(font, MUHAMMAD);
        var mirrored = Paragraph.of(latin, "dmhm");

        assertTrue(arabic.isBidirectional());
        assertEquals(mirrored.widthBetween(0, 4), arabic.widthBetween(0, 4), 1e-3, "the same advances");
        // At the left: with no direction given the word reads from the right,
        // and `start` would put it at the right of the box.
        var left = TextFlow.NORMAL.textAlign(TextAlign.LEFT);
        var drawn = draw(80, 24, frame -> arabic.paint(frame, 4, 2, 72, 0xFF000000, left));
        var expected = draw(80, 24, frame -> mirrored.paint(frame, 4, 2, 72, 0xFF000000, left));
        var differing = 0;
        for (var y = 0; y < 24; y++) {
            for (var x = 0; x < 80; x++) {
                if (drawn.argb(x, y) != expected.argb(x, y)) {
                    differing++;
                }
            }
        }
        assertEquals(0, differing, "pixels that differ from `dmhm`");
    }

    @Test
    @DisplayName("forced left to right, the same word is drawn in logical order")
    void overrideDrawsLogically() {
        var forced =
                Paragraph.of(font, MUHAMMAD, 0, 0, new Directionality(Direction.LTR, Directionality.Source.OVERRIDE));
        var logical = Paragraph.of(latin, "mhmd");

        assertFalse(forced.isBidirectional());
        var drawn = draw(80, 24, frame -> forced.paint(frame, 4, 2, 72, 0xFF000000));
        var expected = draw(80, 24, frame -> logical.paint(frame, 4, 2, 72, 0xFF000000));
        for (var y = 0; y < 24; y++) {
            for (var x = 0; x < 80; x++) {
                assertEquals(expected.argb(x, y), drawn.argb(x, y), "pixel " + x + "," + y);
            }
        }
    }

    @Test
    @DisplayName("the caret in a right-to-left word walks from the right edge to the left")
    void caretWalksLeft() {
        var paragraph = Paragraph.of(font, MUHAMMAD);
        var width = paragraph.widthBetween(0, 4);

        assertEquals(width, paragraph.caretX(0, 4, 0), 1e-9, "before the first letter is the right edge");
        assertEquals(0, paragraph.caretX(0, 4, 4), 1e-9, "after the last is the left edge");
        for (var offset = 1; offset <= 4; offset++) {
            assertTrue(
                    paragraph.caretX(0, 4, offset) < paragraph.caretX(0, 4, offset - 1),
                    "the caret moves left at offset " + offset);
        }
        assertEquals(4, paragraph.offsetAt(0, 4, -20), "left of the line is the end of the word");
        assertEquals(0, paragraph.offsetAt(0, 4, width + 20), "right of it is its start");
    }

    @Test
    @DisplayName("every caret in mixed text is in its own place, and a click on it lands on it")
    void mixedRoundTrip() {
        var text = "Ann " + StandInFace.ARABIC + " 12";
        var paragraph = Paragraph.of(font, text);
        var length = text.length();

        assertEquals(Direction.LTR, paragraph.directionAt(0));
        var places = new HashSet<Long>();
        for (var offset = 0; offset <= length; offset++) {
            var x = paragraph.caretX(0, length, offset);
            assertTrue(places.add(Math.round(x * 1000)), "a second caret at " + x + ", offset " + offset);
            assertEquals(offset, paragraph.offsetAt(0, length, x), "a click on the caret at " + offset);
        }
    }

    @Test
    @DisplayName("a range across the two directions is two stretches as wide as the range")
    void stretches() {
        var text = "Ann " + MUHAMMAD;
        var paragraph = Paragraph.of(font, text);

        // `n `, then the first two Arabic letters, which are at the right end.
        var stretches = paragraph.stretchesOf(0, 8, 2, 6);
        assertEquals(2, stretches.size(), stretches.toString());
        assertEquals(paragraph.widthBetween(0, 2), stretches.get(0).left(), 1e-9);
        assertEquals(paragraph.widthBetween(0, 4), stretches.get(0).right(), 1e-9);
        assertEquals(paragraph.widthBetween(0, 8), stretches.get(1).right(), 1e-9);
        assertEquals(
                paragraph.widthBetween(2, 6),
                stretches.get(0).width() + stretches.get(1).width(),
                1e-9);
        assertEquals(
                List.of(new Stretch(paragraph.widthBetween(0, 1), paragraph.widthBetween(0, 3))),
                paragraph.stretchesOf(0, 8, 1, 3),
                "a range in one run is one stretch, as it always was");
    }

    @Test
    @DisplayName("a space after right-to-left text is left of it, where the next word goes")
    void trailingSpace() {
        var text = MUHAMMAD + " ";
        var paragraph = Paragraph.of(font, text);

        assertTrue(paragraph.caretX(0, 5, 5) < 0, "the caret after the space is left of the line");
        assertEquals(-paragraph.widthBetween(4, 5), paragraph.caretX(0, 5, 5), 1e-9);
        var stretches = paragraph.stretchesOf(0, 5, 3, 5);
        assertEquals(new Stretch(-paragraph.widthBetween(4, 5), paragraph.widthBetween(3, 4)), stretches.getFirst());
    }

    @Test
    @DisplayName("under the first strong character each hard line starts at its own side")
    void plaintextLines() {
        var text = "Ann\n" + MUHAMMAD;
        var paragraph = Paragraph.of(font, text, 0, 0, Directionality.AUTO);
        var layout = paragraph.layout(200);
        var first = layout.lines().get(0);
        var second = layout.lines().get(1);

        assertEquals(Direction.LTR, paragraph.directionAt(first.start()));
        assertEquals(Direction.RTL, paragraph.directionAt(second.start()));
        assertEquals(0, paragraph.indentOf(first, TextAlign.START, 200), 1e-9);
        assertEquals(200 - second.width(), paragraph.indentOf(second, TextAlign.START, 200), 1e-9);
        assertEquals(
                200 - second.width(),
                paragraph.indentOf(second, TextAlign.JUSTIFY, 200),
                1e-9,
                "a justified last line starts at its start");
        assertEquals(0, paragraph.indentOf(second, TextAlign.LEFT, 200), 1e-9, "a side is a side");
    }

    @Test
    @DisplayName("the base direction is part of a paragraph, and of the cache's key")
    void cacheKey() {
        var cache = ParagraphCache.create();
        var rtl = cache.paragraph(latin, "abc!", 0, 0, Directionality.RTL);
        var ltr = cache.paragraph(latin, "abc!", 0, 0, Directionality.LTR);

        assertNotSame(rtl, ltr);
        assertSame(rtl, cache.paragraph(latin, "abc!", 0, 0, Directionality.RTL));
        assertEquals(Directionality.RTL, rtl.directionality());
        assertTrue(rtl.isBidirectional());
        assertFalse(ltr.isBidirectional());
        // `!` is right to left at the base level, so it goes to the left.
        assertEquals(rtl.widthBetween(3, 4), rtl.caretX(0, 4, 3), 1e-9);
    }

    @Test
    @DisplayName("a joined paragraph is one text to the algorithm")
    void joined() {
        var regular = Paragraph.of(font, "Ann ");
        var name = Paragraph.of(font, MUHAMMAD);
        var joined = Paragraph.join(List.of(regular, name));

        assertTrue(joined.isBidirectional());
        assertEquals(Direction.LTR, joined.directionAt(0), "the first span's directionality: its first strong");
        assertEquals(joined.widthBetween(0, 8), joined.caretX(0, 8, 4), 1e-9, "the name starts at the right");
        var drawn = draw(100, 24, frame -> joined.paint(frame, 4, 2, 92, 0xFF000000));
        var expected =
                draw(100, 24, frame -> Paragraph.of(font, "Ann " + MUHAMMAD).paint(frame, 4, 2, 92, 0xFF000000));
        for (var y = 0; y < 24; y++) {
            for (var x = 0; x < 100; x++) {
                assertEquals(expected.argb(x, y), drawn.argb(x, y), "pixel " + x + "," + y);
            }
        }
    }

    @Test
    @DisplayName("a cut right-to-left line keeps its start at the right and puts the mark at the left")
    void truncatedRightToLeft() {
        var text = StandInFace.ARABIC + " " + StandInFace.ARABIC + " " + StandInFace.ARABIC;
        var paragraph = Paragraph.of(font, text);
        var flow = new TextFlow(WhiteSpace.NOWRAP, TextOverflow.ELLIPSIS, TextAlign.RIGHT);
        var image = draw(120, 24, frame -> paragraph.paint(frame, 10, 2, 80, 0xFF000000, flow));

        for (var x = 0; x < 10; x++) {
            assertFalse(inked(image, x, 0, 24), "nothing left of the box at " + x);
        }
        for (var x = 91; x < 120; x++) {
            assertFalse(inked(image, x, 0, 24), "nothing right of it at " + x);
        }
        var baseline = (int) Math.round(2 + font.ascent());
        var markColumns = 0;
        for (var x = 10; x < 16; x++) {
            assertFalse(inked(image, x, 2, baseline - 4), "only the mark's dots at the left, at " + x);
            markColumns += inked(image, x, baseline - 4, baseline + 1) ? 1 : 0;
        }
        assertTrue(markColumns > 0, "and the mark is there");
        var right = false;
        for (var x = 84; x < 90; x++) {
            right |= inked(image, x, 2, baseline - 4);
        }
        assertTrue(right, "the kept text reaches the right edge");
    }

    @Test
    @DisplayName("a justified right-to-left paragraph fills its lines, and its last line starts at the right")
    void justifiedRightToLeft() {
        var text = StandInFace.ARABIC + " " + StandInFace.ARABIC + " " + MUHAMMAD;
        var paragraph = Paragraph.of(font, text);
        var flow = new TextFlow(WhiteSpace.NORMAL, TextOverflow.CLIP, TextAlign.JUSTIFY);
        var width = paragraph.widthBetween(0, 17) + 6;
        var layout = paragraph.layout(width);
        assertEquals(2, layout.lineCount(), "the last word wraps");

        var box = (int) Math.ceil(width);
        var image = draw(box + 20, 40, frame -> paragraph.paint(frame, 10, 2, width, 0xFF000000, flow));
        var lineHeight = (int) Math.floor(font.lineHeight());
        assertTrue(inked(image, 11, 2, 2 + lineHeight), "the first line reaches the left edge");
        assertTrue(inked(image, 8 + box, 2, 2 + lineHeight), "and the right");
        // Its middle third, clear of the descenders of the line above.
        var top = 2 + lineHeight + lineHeight / 3;
        var bottom = 2 + 2 * lineHeight - lineHeight / 3;
        assertTrue(inked(image, 8 + box, top, bottom), "the last line is at the right");
        for (var x = 10; x < 30; x++) {
            assertFalse(inked(image, x, top, bottom), "and not at the left, at " + x);
        }
    }
}
