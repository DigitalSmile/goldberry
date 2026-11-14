package dev.goldberry.text.document;

import static dev.goldberry.text.mark.MarkKind.Style.BOLD;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import dev.goldberry.RendererRequirement;
import dev.goldberry.assets.BundledFont;
import dev.goldberry.text.Paragraph;
import dev.goldberry.text.font.Font;
import dev.goldberry.text.font.Fonts;
import dev.goldberry.text.mark.Mark;
import dev.goldberry.text.mark.MarkedText;

/// A line is its words and its marks: either changing shapes it again, and
/// nothing else does.
class TextDocumentMarksTest {

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

    /// Records each line it shapes, with the marks it was handed.
    private final class Counting implements TextDocument.MarkedShaper {

        private final List<String> shaped = new ArrayList<>();
        private final List<List<Mark>> marks = new ArrayList<>();

        @Override
        public Paragraph shape(String line, List<Mark> lineMarks) {
            shaped.add(line);
            marks.add(lineMarks);
            return Paragraph.of(font, line);
        }
    }

    @Test
    @DisplayName("each line is handed the marks on it, counted from its start")
    void lineMarks() {
        var shaper = new Counting();
        var text = MarkedText.of("one\ntwo\nthree").mark(2, 6, BOLD);
        var document = TextDocument.of(font, text, null, shaper);
        assertEquals(List.of("one", "two", "three"), shaper.shaped);
        assertEquals(List.of(List.of(new Mark(2, 3, BOLD)), List.of(new Mark(0, 2, BOLD)), List.of()), shaper.marks);
        assertEquals(List.of(new Mark(0, 2, BOLD)), document.marksOf(1));
    }

    @Test
    @DisplayName("a word made bold shapes its line again, and only its line")
    void markOnly() {
        var shaper = new Counting();
        var text = MarkedText.of("one\ntwo\nthree");
        var first = TextDocument.of(font, text, null, shaper);
        shaper.shaped.clear();

        var second = TextDocument.of(font, text.mark(4, 7, BOLD), first, shaper);
        assertEquals(List.of("two"), shaper.shaped);
        assertSame(first.paragraphOf(0), second.paragraphOf(0));
        assertSame(first.paragraphOf(2), second.paragraphOf(2));
    }

    @Test
    @DisplayName("typing on line one moves the marks below it without shaping their lines")
    void marksMoveWithTheText() {
        var shaper = new Counting();
        var text = MarkedText.of("one\ntwo\nthree").mark(8, 13, BOLD);
        var first = TextDocument.of(font, text, null, shaper);
        shaper.shaped.clear();

        var typed = text.replace(3, 3, MarkedText.of("!"), text.kindsInheritedAt(3));
        var second = TextDocument.of(font, typed, first, shaper);
        assertEquals(List.of("one!"), shaper.shaped);
        assertSame(first.paragraphOf(2), second.paragraphOf(2));
        assertEquals(List.of(new Mark(0, 5, BOLD)), second.marksOf(2));
    }

    @Test
    @DisplayName("the same text with the same marks is the same document")
    void unchanged() {
        var shaper = new Counting();
        var text = MarkedText.of("one\ntwo").mark(0, 3, BOLD);
        var first = TextDocument.of(font, text, null, shaper);
        assertSame(first, TextDocument.of(font, MarkedText.of("one\ntwo").mark(0, 3, BOLD), first, shaper));
    }
}
