package dev.goldberry.text.document;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import dev.goldberry.RendererRequirement;
import dev.goldberry.assets.BundledFont;
import dev.goldberry.text.Paragraph;
import dev.goldberry.text.edit.Editor;
import dev.goldberry.text.flow.OverflowWrap;
import dev.goldberry.text.flow.TextAlign;
import dev.goldberry.text.flow.TextFlow;
import dev.goldberry.text.flow.WordBreak;
import dev.goldberry.text.font.Font;

/// A document broken where its paint breaks it: a word `overflow-wrap` cuts is
/// cut in the rows an editor counts, so the caret, the selection and the scroll
/// agree with what is drawn.
///
/// The defect this is for: a text area painted its rows through the cascade's
/// flow, which cut a long word, and counted them through the document, which did
/// not -- so the paint drew a line the control did not know it had.
@DisplayName("a document's rows and the word a flow cuts")
class DocumentWordBreakTest {

    /// One word far wider than the box, then two short ones -- a log line with a
    /// digest in it.
    private static final String LOG =
            "9f86d081884c7d659a2feaa0c55ad015a3bf4f1b2b0b822cd15d6c15b0f00a08 restarted twice";

    private static final double WIDTH = 100;

    private static final TextFlow ANYWHERE = TextFlow.NORMAL.overflowWrap(OverflowWrap.ANYWHERE);

    private Font font;

    @BeforeEach
    void openFont() {
        RendererRequirement.enforce();
        font = Font.bundled(BundledFont.CODE, 14);
    }

    @AfterEach
    void closeFont() {
        if (font != null) {
            font.close();
        }
    }

    private TextDocument document(String text) {
        return TextDocument.of(font, text, null, line -> Paragraph.of(font, line));
    }

    @Test
    @DisplayName("has as many rows as the paragraph the paint lays out with the same flow")
    void rowsMatchThePaint() {
        var document = document(LOG);

        var rows = document.lines(WIDTH, ANYWHERE).size();

        assertEquals(Paragraph.of(font, LOG).layout(WIDTH, ANYWHERE).lineCount(), rows);
        assertTrue(rows > document.lines(WIDTH).size(), "the digest was cut, so there are more rows than without it");
    }

    @Test
    @DisplayName("keeps a word whole when asked without a flow, as it always did")
    void withoutAFlowNothingIsCut() {
        var document = document(LOG);

        assertEquals(
                Paragraph.of(font, LOG).layout(WIDTH).lineCount(),
                document.lines(WIDTH).size());
        assertEquals(
                document.lines(WIDTH).size(),
                document.lines(WIDTH, TextFlow.NORMAL).size());
    }

    @Test
    @DisplayName("word-break: break-all fills every row, so it never has fewer rows than overflow-wrap")
    void breakAllFillsRows() {
        var document = document(LOG);
        var breakAll = TextFlow.NORMAL.wordBreak(WordBreak.BREAK_ALL);

        assertTrue(document.lines(WIDTH, breakAll).size()
                >= document.lines(WIDTH, ANYWHERE).size());
    }

    @Nested
    @DisplayName("the memo")
    class TheMemo {

        @Test
        @DisplayName("is kept for the same width and the same breaking")
        void keptForTheSameBreaking() {
            var document = document(LOG);

            var first = document.lines(WIDTH, ANYWHERE);

            assertSame(first, document.lines(WIDTH, ANYWHERE));
        }

        @Test
        @DisplayName("is kept when only the alignment changed, which moves no break")
        void keptAcrossAlignment() {
            var document = document(LOG);

            var first = document.lines(WIDTH, ANYWHERE);

            assertSame(first, document.lines(WIDTH, ANYWHERE.textAlign(TextAlign.CENTER)));
        }

        @Test
        @DisplayName("is dropped when the breaking changed, at the same width")
        void droppedForOtherBreaking() {
            var document = document(LOG);

            var whole = document.lines(WIDTH);
            var cut = document.lines(WIDTH, ANYWHERE);

            assertNotSame(whole, cut);
            assertTrue(cut.size() > whole.size());
        }
    }

    @Nested
    @DisplayName("an editor given the flow")
    class AnEditor {

        @Test
        @DisplayName("puts a caret inside the cut word on the row the paint drew that part on")
        void caretFollowsTheCut() {
            var editor = new Editor(font).multiline(true).wrapWidth(WIDTH).textFlow(ANYWHERE);
            editor.text(LOG);
            // Two thirds into the digest: past the first cut, before the space.
            editor.caretTo(40, false);

            assertTrue(editor.caret().line() > 0, "the caret is on a row the cut made, not the first");
        }

        @Test
        @DisplayName("without it, keeps the whole word on the first row")
        void withoutItTheWordIsOneRow() {
            var editor = new Editor(font).multiline(true).wrapWidth(WIDTH);
            editor.text(LOG);
            editor.caretTo(40, false);

            assertEquals(0, editor.caret().line());
        }

        @Test
        @DisplayName("takes its alignment from the flow too")
        void takesTheAlignment() {
            var editor = new Editor(font).textFlow(ANYWHERE.textAlign(TextAlign.END));

            assertEquals(TextAlign.END, editor.textAlign());
        }
    }
}
