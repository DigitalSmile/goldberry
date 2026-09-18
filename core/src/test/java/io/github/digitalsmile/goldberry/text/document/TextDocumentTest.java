package io.github.digitalsmile.goldberry.text.document;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import io.github.digitalsmile.goldberry.RendererRequirement;
import io.github.digitalsmile.goldberry.assets.BundledFont;
import io.github.digitalsmile.goldberry.text.Paragraph;
import io.github.digitalsmile.goldberry.text.font.Font;
import io.github.digitalsmile.goldberry.text.font.Fonts;

/// A document shaped a hard line at a time — [ADR-0388], `docs/gaps.md` G44.
///
/// The whole class is one claim: **the pieces agree with the whole**. A
/// [TextDocument] must break, measure and address exactly as a [Paragraph] of
/// the same text does, and an incrementally rebuilt one must agree with a fresh
/// one. Everything that could go wrong here goes wrong as a caret half a line
/// out of place in a document nobody has, so the whole paragraph is kept beside
/// it as the answer.
class TextDocumentTest {

    private static final double WIDTH = 220;

    private Fonts fonts;
    private Font font;

    @BeforeEach
    void setUp() {
        RendererRequirement.enforce();
        fonts = Fonts.bundled();
        font = fonts.of(BundledFont.UI, 14);
    }

    @AfterEach
    void tearDown() {
        if (fonts != null) {
            fonts.close();
        }
    }

    /// A shaper that counts, so "only the line that changed" is a number.
    private final class Counting implements TextDocument.Shaper {

        private final List<String> shaped = new ArrayList<>();

        @Override
        public Paragraph shape(String line) {
            shaped.add(line);
            return Paragraph.of(font, line);
        }

        int count() {
            return shaped.size();
        }

        void reset() {
            shaped.clear();
        }
    }

    private TextDocument document(String text) {
        return TextDocument.of(font, text, null, line -> Paragraph.of(font, line));
    }

    /// Prose with hard lines, blank lines and lines long enough to wrap at
    /// [#WIDTH] — all three cases the wrap distinguishes.
    private static final String NOTE = "A short line.\n"
            + "\n"
            + "A much longer line that will certainly not fit inside the column this"
            + " test wraps at, and so becomes several.\n"
            + "Another.\n"
            + "\n";

    @Test
    @DisplayName("it breaks where the whole paragraph breaks")
    void theLinesAgree() {
        var document = document(NOTE);
        var whole = Paragraph.of(font, NOTE).layout(WIDTH);

        var lines = document.lines(WIDTH);
        assertEquals(whole.lines().size(), lines.size(), "the same number of visual lines");
        for (var i = 0; i < lines.size(); i++) {
            assertEquals(whole.lines().get(i).start(), lines.get(i).start(), "line " + i + " starts");
            assertEquals(whole.lines().get(i).end(), lines.get(i).end(), "line " + i + " ends");
            assertEquals(whole.lines().get(i).width(), lines.get(i).width(), 0.001, "line " + i + " is as wide");
        }
    }

    @Test
    @DisplayName("it measures and addresses where the whole paragraph does")
    void theMeasurementsAgree() {
        var document = document(NOTE);
        var whole = Paragraph.of(font, NOTE);
        var lines = document.lines(WIDTH);

        for (var i = 0; i < lines.size(); i++) {
            var line = lines.get(i);
            assertEquals(
                    whole.widthBetween(line.start(), line.end()),
                    document.widthBetween(line.start(), line.end()),
                    0.001,
                    "line " + i + " measures the same");
            // Every caret position on the line, from a click at every tenth of
            // its width — which is what a hit test actually asks.
            for (var step = 0; step <= 10; step++) {
                var x = line.width() * step / 10.0;
                assertEquals(
                        whole.offsetAt(line.start(), line.end(), x),
                        document.offsetAt(line.start(), line.end(), x),
                        "line " + i + " at " + x + " lands on the same offset");
            }
        }
    }

    @Test
    @DisplayName("a visual line's index is the one a walk would find")
    void theIndexAgrees() {
        var document = document(NOTE);
        var lines = document.lines(WIDTH);
        for (var offset = 0; offset <= NOTE.length(); offset++) {
            var walked = 0;
            for (var i = 0; i < lines.size(); i++) {
                if (lines.get(i).start() <= offset) {
                    walked = i;
                }
            }
            assertEquals(walked, lines.indexOf(offset), "offset " + offset);
        }
    }

    @Test
    @DisplayName("a document rebuilt from the last one is the document it would have been")
    void theIncrementalRebuildAgrees() {
        // A typed character, a newline, a deletion, a paste of several lines,
        // and an edit at the very front — the shapes an editor actually makes.
        var edits = List.of(
                NOTE + "x",
                NOTE.replace("Another.", "Another one."),
                NOTE.replace("A short line.\n", ""),
                "Pasted.\nTwo lines.\n" + NOTE,
                NOTE.replace("\n\n", "\n"),
                "",
                "one line, no newline at all");

        var document = document(NOTE);
        for (var text : edits) {
            document = TextDocument.of(font, text, document, line -> Paragraph.of(font, line));
            var fresh = document(text);

            assertEquals(fresh.hardLineCount(), document.hardLineCount(), "hard lines after " + summary(text));
            var rebuilt = document.lines(WIDTH);
            var built = fresh.lines(WIDTH);
            assertEquals(built.size(), rebuilt.size(), "visual lines after " + summary(text));
            for (var i = 0; i < built.size(); i++) {
                assertEquals(built.get(i), rebuilt.get(i), "line " + i + " after " + summary(text));
            }
            for (var k = 0; k < fresh.hardLineCount(); k++) {
                assertEquals(fresh.startOf(k), document.startOf(k), "hard line " + k + " starts");
                assertEquals(fresh.endOf(k), document.endOf(k), "hard line " + k + " ends");
            }
        }
    }

    private static String summary(String text) {
        return "a document of " + text.length() + " characters";
    }

    @Test
    @DisplayName("a keystroke re-shapes the line it changed and nothing else")
    void onlyTheChangedLineIsShaped() {
        var shaper = new Counting();
        var document = TextDocument.of(font, NOTE, null, shaper);
        assertEquals(document.hardLineCount(), shaper.count(), "one shaping per hard line to open it");

        shaper.reset();
        var typed = NOTE.replace("Another.", "Another.x");
        TextDocument.of(font, typed, document, shaper);
        assertEquals(1, shaper.count(), "and one for the line a keystroke changed");
        assertEquals("Another.x", shaper.shaped.getFirst(), "which is that line");
    }

    @Test
    @DisplayName("a document that did not change is the same document")
    void anUnchangedTextIsNotRebuilt() {
        var shaper = new Counting();
        var document = TextDocument.of(font, NOTE, null, shaper);
        shaper.reset();
        // A different instance holding the same characters, which is what a
        // rebuild produces: nothing may be re-shaped for it.
        var again = TextDocument.of(font, new StringBuilder(NOTE).toString(), document, shaper);
        assertSame(document, again, "the same document, memo and all");
        assertEquals(0, shaper.count(), "and nothing re-shaped");
    }

    @Test
    @DisplayName("a new font re-shapes everything, because the glyphs are the font's")
    void aNewFontRebuilds() {
        var shaper = new Counting();
        var document = TextDocument.of(font, NOTE, null, shaper);
        shaper.reset();
        var larger = fonts.of(BundledFont.UI, 18);
        var rebuilt = TextDocument.of(larger, NOTE, document, shaper);
        assertEquals(rebuilt.hardLineCount(), shaper.count(), "every line again");
        assertTrue(
                rebuilt.lines(WIDTH).size() >= document.lines(WIDTH).size(),
                "and a larger face wraps into at least as many rows");
    }

    @Test
    @DisplayName("an empty document is one empty line, which is where a caret goes")
    void anEmptyDocumentHasALine() {
        var document = document("");
        assertEquals(1, document.hardLineCount());
        assertEquals(1, document.lines(WIDTH).size());
        assertEquals(0, document.lines(WIDTH).get(0).end());
    }
}
