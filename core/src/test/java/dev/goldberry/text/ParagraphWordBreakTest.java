package dev.goldberry.text;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import dev.goldberry.RendererRequirement;
import dev.goldberry.assets.BundledFont;
import dev.goldberry.layout.MeasureMode;
import dev.goldberry.text.flow.OverflowWrap;
import dev.goldberry.text.flow.TextFlow;
import dev.goldberry.text.flow.WordBreak;
import dev.goldberry.text.font.Font;

/// `overflow-wrap: anywhere` and `word-break: break-all`: a word too wide for
/// the line is cut between grapheme clusters, which is what a log line with a
/// long path in it needs.
class ParagraphWordBreakTest {

    /// One word far wider than any box here, a digest with no hyphen or slash
    /// for the line breaker to use, then two short ones.
    private static final String LOG =
            "9f86d081884c7d659a2feaa0c55ad015a3bf4f1b2b0b822cd15d6c15b0f00a08 restarted twice";

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

    private static final TextFlow ANYWHERE = TextFlow.NORMAL.overflowWrap(OverflowWrap.ANYWHERE);
    private static final TextFlow BREAK_ALL = TextFlow.NORMAL.wordBreak(WordBreak.BREAK_ALL);

    @Test
    @DisplayName("without it, a word wider than the line overflows on a line of its own")
    void normalOverflows() {
        var paragraph = Paragraph.of(font, LOG);
        var layout = paragraph.layout(100);
        assertTrue(layout.width() > 100, "the long word sticks out, which is the reported defect");
    }

    @Test
    @DisplayName("overflow-wrap: anywhere cuts that word between graphemes, and no line is wider than the box")
    void anywhereCutsTheWord() {
        var paragraph = Paragraph.of(font, LOG);
        var layout = paragraph.layout(100, ANYWHERE);

        for (var line : layout.lines()) {
            assertTrue(line.width() <= 100.01, "a line of " + line.width());
        }
        assertTrue(layout.lines().size() >= 3, "the word took several lines");
        assertEquals(LOG, joined(paragraph, layout), "and nothing was lost or doubled");
    }

    @Test
    @DisplayName("but a word that fits on a line of its own still moves to the next line whole")
    void anywhereKeepsShortWords() {
        var text = "short words only here";
        var paragraph = Paragraph.of(font, text);
        var normal = paragraph.layout(90);
        var anywhere = paragraph.layout(90, ANYWHERE);
        assertEquals(normal, anywhere, "nothing is wider than a line, so nothing is cut");
    }

    @Test
    @DisplayName("word-break: break-all fills every line to the edge, cutting words where it falls")
    void breakAllFillsLines() {
        var text = "abcdefgh ijklmnop";
        var paragraph = Paragraph.of(font, text);
        var width = font.widthOf("abcdefgh ijk") + 0.5f;
        var layout = paragraph.layout(width, BREAK_ALL);

        var first = layout.lines().getFirst();
        assertEquals("abcdefgh ijk", text.substring(first.start(), first.end()), "cut mid-word at the edge");
        assertEquals(text, joined(paragraph, layout));
    }

    @Test
    @DisplayName("a box narrower than one character still takes one grapheme a line, and finishes")
    void narrowerThanAGrapheme() {
        var paragraph = Paragraph.of(font, "abc");
        var layout = paragraph.layout(1, ANYWHERE);
        assertEquals(3, layout.lines().size());
    }

    @Test
    @DisplayName("the measure function honours it, so a box can be as narrow as it is offered")
    void measured() {
        var paragraph = Paragraph.of(font, LOG);
        var size =
                paragraph.measureFunction(ANYWHERE).measure(100, MeasureMode.AT_MOST, Float.NaN, MeasureMode.UNDEFINED);
        assertTrue(size.width() <= 100.01, "measured " + size.width());
    }

    @Test
    @DisplayName("one paragraph asked under two flows answers each correctly, memo or not")
    void memoIsPerFlow() {
        var paragraph = Paragraph.of(font, LOG);
        var cut = paragraph.layout(100, ANYWHERE).lines().size();
        var whole = paragraph.layout(100).lines().size();
        assertTrue(cut > whole);
        assertEquals(cut, paragraph.layout(100, ANYWHERE).lines().size());
    }

    private static String joined(Paragraph paragraph, TextLayout layout) {
        var text = new StringBuilder();
        for (var line : layout.lines()) {
            text.append(paragraph.text(), line.start(), line.end());
        }
        return text.toString();
    }
}
