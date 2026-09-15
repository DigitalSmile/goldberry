package io.github.digitalsmile.goldberry.text.edit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import io.github.digitalsmile.goldberry.RendererRequirement;
import io.github.digitalsmile.goldberry.assets.BundledFont;
import io.github.digitalsmile.goldberry.text.Paragraph;
import io.github.digitalsmile.goldberry.text.flow.TextAlign;
import io.github.digitalsmile.goldberry.text.font.Font;

/// Where a caret is — ADR-0285.
///
/// Against **real shaping**, because that is the whole subject: a caret's x is a
/// prefix width of a shaped run, and a test that measured it by counting
/// characters would pass while the caret sat in the middle of a kerned pair.
///
/// The assertions are therefore relational rather than absolute — the caret after
/// three characters is further right than after two, and lands back where it
/// started after a round trip through a click — because the exact pixel depends on
/// a font file and is not a fact about this class.
class TextGeometryTest {

    private static final String WRAPPED = "the quick brown fox jumps over the lazy dog";

    private Font font;

    @BeforeEach
    void setUp() {
        RendererRequirement.enforce();
        font = Font.bundled(BundledFont.UI, 13);
    }

    @AfterEach
    void tearDown() {
        if (font != null) {
            font.close();
        }
    }

    private Paragraph paragraph(String text) {
        return Paragraph.of(font, text);
    }

    @Test
    @DisplayName("the caret advances as the offset does, and starts at zero")
    void caretAdvances() {
        var paragraph = paragraph("hello");
        var layout = paragraph.layout(Paragraph.UNCONSTRAINED);

        var start = TextGeometry.caretAt(paragraph, layout, 0);
        assertEquals(0, start.x(), 0.001, "the caret before the first character is at the left edge");
        assertEquals(0, start.line());
        assertEquals(font.lineHeight(), start.height(), 0.001, "a caret is a line tall");

        var previous = 0.0;
        for (var offset = 1; offset <= 5; offset++) {
            var x = TextGeometry.caretAt(paragraph, layout, offset).x();
            assertTrue(x > previous, "the caret at " + offset + " is right of the one at " + (offset - 1));
            previous = x;
        }
    }

    @Test
    @DisplayName("a click comes back to the offset the caret was drawn at")
    void hitTestRoundTrips() {
        var paragraph = paragraph("hello world");
        var layout = paragraph.layout(Paragraph.UNCONSTRAINED);

        // The round trip is the property worth asserting: whatever the font does,
        // clicking where the caret is drawn must not move it.
        for (var offset = 0; offset <= "hello world".length(); offset++) {
            var caret = TextGeometry.caretAt(paragraph, layout, offset);
            assertEquals(
                    offset,
                    TextGeometry.offsetAt(paragraph, layout, caret.x(), caret.top() + 1),
                    "clicking the caret at " + offset + " keeps it there");
        }
    }

    @Test
    @DisplayName("a click left of the text is the start and right of it is the end")
    void hitTestClamps() {
        var paragraph = paragraph("hello");
        var layout = paragraph.layout(Paragraph.UNCONSTRAINED);

        assertEquals(0, TextGeometry.offsetAt(paragraph, layout, -50, 0));
        assertEquals(5, TextGeometry.offsetAt(paragraph, layout, 10_000, 0));
        assertEquals(0, TextGeometry.offsetAt(paragraph, layout, 0, -100), "above the text is the first line");
        assertEquals(5, TextGeometry.offsetAt(paragraph, layout, 10_000, 10_000), "below it is the last");
    }

    @Test
    @DisplayName("a wrapped paragraph puts later lines lower down")
    void wrappedLinesStack() {
        var paragraph = paragraph(WRAPPED);
        var layout = paragraph.layout(80);
        assertTrue(layout.lineCount() > 1, "the text wraps at 80 logical pixels");

        var first = TextGeometry.caretAt(paragraph, layout, 0);
        var second =
                TextGeometry.caretAt(paragraph, layout, layout.lines().get(1).start());

        assertEquals(0, first.line());
        assertEquals(1, second.line());
        assertEquals(font.lineHeight(), second.top() - first.top(), 0.001);
    }

    @Test
    @DisplayName("a hard newline is a line, whatever the wrap width is")
    void hardNewlinesAreLines() {
        var paragraph = paragraph("one\ntwo\nthree");
        var layout = paragraph.layout(Paragraph.UNCONSTRAINED);

        assertEquals(3, layout.lineCount());
        assertEquals(0, TextGeometry.lineOf(layout, 0));
        assertEquals(0, TextGeometry.lineOf(layout, 3), "the offset before a newline is on the line it ends");
        assertEquals(1, TextGeometry.lineOf(layout, 4), "and the one after it starts the next");
        assertEquals(2, TextGeometry.lineOf(layout, 13));
    }

    @Test
    @DisplayName("Down keeps the column through a short line")
    void verticalMovementKeepsTheColumn() {
        // The property every editor has and nobody names until it is missing:
        // walking down through a short line and out the other side comes back to
        // the column it started in, rather than to the end of the short line.
        var paragraph = paragraph("aaaaaaaaaa\nbb\ncccccccccc");
        var layout = paragraph.layout(Paragraph.UNCONSTRAINED);

        var from = 8; // eight characters into the first line
        var column = TextGeometry.caretAt(paragraph, layout, from).x();

        var middle = TextGeometry.moveLine(paragraph, layout, from, 1, column);
        assertEquals(13, middle, "the short line has nowhere near that column, so the caret sits at its end");

        var bottom = TextGeometry.moveLine(paragraph, layout, middle, 1, column);
        assertTrue(bottom > 14, "the third line takes the column back, rather than staying at its start");
        assertTrue(
                Math.abs(TextGeometry.caretAt(paragraph, layout, bottom).x() - column) < font.size(),
                "and lands within a character of the column it started in");
    }

    @Test
    @DisplayName("moving off the top or the bottom lands at the ends of the text")
    void verticalMovementClamps() {
        var paragraph = paragraph("one\ntwo");
        var layout = paragraph.layout(Paragraph.UNCONSTRAINED);

        assertEquals(0, TextGeometry.moveLine(paragraph, layout, 2, -5, Double.NaN));
        assertEquals(7, TextGeometry.moveLine(paragraph, layout, 2, 5, Double.NaN));
    }

    @Test
    @DisplayName("a selection on one line is one rectangle, and across two is two")
    void selectionRectangles() {
        var paragraph = paragraph("one\ntwo");
        var layout = paragraph.layout(Paragraph.UNCONSTRAINED);

        assertEquals(List.of(), TextGeometry.selectionRects(paragraph, layout, 2, 2), "a caret is not a selection");

        var oneLine = TextGeometry.selectionRects(paragraph, layout, 0, 3);
        assertEquals(1, oneLine.size());
        assertEquals(0, oneLine.getFirst().top(), 0.001);
        assertTrue(oneLine.getFirst().width() > 0);

        var twoLines = TextGeometry.selectionRects(paragraph, layout, 1, 6);
        assertEquals(2, twoLines.size(), "a selection across a break is two rectangles, not one box");
        assertTrue(twoLines.get(0).left() > 0, "the first starts where the selection did");
        assertEquals(0, twoLines.get(1).left(), 0.001, "the second starts at the left edge");
        assertEquals(font.lineHeight(), twoLines.get(1).top(), 0.001);
    }

    @Test
    @DisplayName("a selected newline is visible as a space at the end of the line")
    void selectedBreaksHaveWidth() {
        var paragraph = paragraph("a\nb");
        var layout = paragraph.layout(Paragraph.UNCONSTRAINED);

        var acrossTheBreak =
                TextGeometry.selectionRects(paragraph, layout, 0, 3).getFirst();
        var justTheCharacter =
                TextGeometry.selectionRects(paragraph, layout, 0, 1).getFirst();

        assertTrue(
                acrossTheBreak.width() > justTheCharacter.width(),
                "the break itself is inside the selection and has to be drawn as something");
    }

    @Test
    @DisplayName("the selection is symmetric: dragging backwards selects the same thing")
    void selectionIsSymmetric() {
        var paragraph = paragraph("hello world");
        var layout = paragraph.layout(Paragraph.UNCONSTRAINED);

        assertEquals(
                TextGeometry.selectionRects(paragraph, layout, 2, 7),
                TextGeometry.selectionRects(paragraph, layout, 7, 2));
    }

    @Test
    @DisplayName("an offset outside the text is refused rather than measured")
    void refusesOffsetsOutside() {
        var paragraph = paragraph("hello");
        var layout = paragraph.layout(Paragraph.UNCONSTRAINED);

        assertThrows(IndexOutOfBoundsException.class, () -> TextGeometry.caretAt(paragraph, layout, 6));
        assertThrows(IndexOutOfBoundsException.class, () -> TextGeometry.caretAt(paragraph, layout, -1));
        assertThrows(IndexOutOfBoundsException.class, () -> TextGeometry.selectionRects(paragraph, layout, 0, 9));
    }

    @Test
    @DisplayName("a caret rectangle is as wide as it is asked to be")
    void caretHasNoWidthOfItsOwn() {
        var paragraph = paragraph("hi");
        var layout = paragraph.layout(Paragraph.UNCONSTRAINED);
        var caret = TextGeometry.caretAt(paragraph, layout, 1);

        assertEquals(2, caret.rect(2).width(), 0.001);
        assertEquals(caret.height(), caret.rect(1).height(), 0.001);
        assertNotEquals(0, caret.rect(1).left(), "and it is where the caret is");
    }

    /// `text-align`, which every one of the four questions above had to be told
    /// about — `docs/gaps.md` G30, ADR-0318.
    ///
    /// The paint indents each line by its share of the box's slack, so a caret
    /// measured from the paragraph's origin drifted away from the glyphs by half
    /// that slack under `center` and by all of it under `end`, growing as the line
    /// shortened. The assertions here are the two that catch it: the indent is the
    /// **same rule the painter uses** ([TextAlign#indentOf]), and a click on a
    /// drawn caret comes back to the offset it was drawn for.
    @Nested
    @DisplayName("aligned text")
    class Aligned {

        /// Wide enough that "hello" leaves real slack in it.
        private static final double BOX = 200;

        @Test
        @DisplayName("the three-argument forms still mean start")
        void theShortFormsAreStart() {
            var paragraph = paragraph("hello");
            var layout = paragraph.layout(Paragraph.UNCONSTRAINED);

            assertEquals(
                    TextGeometry.caretAt(paragraph, layout, 3).x(),
                    TextGeometry.caretAt(paragraph, layout, 3, BOX, TextAlign.START)
                            .x(),
                    0.001);
            assertEquals(
                    TextGeometry.selectionRects(paragraph, layout, 0, 3),
                    TextGeometry.selectionRects(paragraph, layout, 0, 3, BOX, TextAlign.START));
        }

        @Test
        @DisplayName("the caret moves in by the painter's own indent")
        void theCaretIsIndentedLikeTheGlyphs() {
            var paragraph = paragraph("hello");
            var layout = paragraph.layout(BOX);
            var line = layout.lines().getFirst();

            var start = TextGeometry.caretAt(paragraph, layout, 2, BOX, TextAlign.START);
            var centred = TextGeometry.caretAt(paragraph, layout, 2, BOX, TextAlign.CENTER);
            var end = TextGeometry.caretAt(paragraph, layout, 2, BOX, TextAlign.END);

            assertEquals(
                    start.x() + TextAlign.CENTER.indentOf(line.width(), BOX),
                    centred.x(),
                    0.001,
                    "the caret and the paint have to use one rule, and this is that rule");
            assertEquals(start.x() + TextAlign.END.indentOf(line.width(), BOX), end.x(), 0.001);
            assertTrue(centred.x() > start.x(), "a centred line starts further in than a left-aligned one");
            assertTrue(end.x() > centred.x());
        }

        /// The round trip the entry names: draw the caret at an offset, press
        /// exactly there, get the offset back — on a **wrapped, centred**
        /// paragraph, where every line has a different indent.
        @Test
        @DisplayName("a click on the caret comes back to its offset, on every line")
        void hitTestRoundTripsWhenCentred() {
            var paragraph = paragraph(WRAPPED);
            var layout = paragraph.layout(BOX);
            assertTrue(layout.lineCount() > 1, "the text has to wrap for the indents to differ");

            for (var offset = 0; offset <= WRAPPED.length(); offset++) {
                var caret = TextGeometry.caretAt(paragraph, layout, offset, BOX, TextAlign.CENTER);
                assertEquals(
                        offset,
                        TextGeometry.offsetAt(paragraph, layout, caret.x(), caret.top() + 1, BOX, TextAlign.CENTER),
                        "clicking the centred caret at " + offset + " moved it");
            }
        }

        /// And the proof that it is not a no-op: the *unaligned* hit test, given a
        /// centred caret's x, answers something else.
        @Test
        @DisplayName("measuring a centred caret with the unaligned form does drift")
        void theUnalignedFormDrifts() {
            // A short line in a wide box, so the slack is most of the box and the
            // drift is most of a word rather than a fraction of a glyph.
            var paragraph = paragraph("hello");
            var layout = paragraph.layout(BOX);

            var offset = 2;
            var caret = TextGeometry.caretAt(paragraph, layout, offset, BOX, TextAlign.CENTER);

            assertNotEquals(
                    offset,
                    TextGeometry.offsetAt(paragraph, layout, caret.x(), caret.top() + 1),
                    "if these agreed there would be nothing for the alignment to carry");
        }

        @Test
        @DisplayName("Down keeps the visual column, not the paragraph's one")
        void verticalMovementKeepsTheVisualColumn() {
            // Lines of very different lengths, so their indents differ by a lot:
            // a `desiredX` read off one line is meaningless on another unless both
            // ends of the round trip know the alignment.
            var paragraph = paragraph("aaaaaaaaaaaaaaaaaaaa\nbb\naaaaaaaaaaaaaaaaaaaa");
            var layout = paragraph.layout(BOX);

            var from = 10;
            var column = TextGeometry.caretAt(paragraph, layout, from, BOX, TextAlign.CENTER)
                    .x();

            var middle = TextGeometry.moveLine(paragraph, layout, from, 1, column, BOX, TextAlign.CENTER);
            var bottom = TextGeometry.moveLine(paragraph, layout, middle, 1, column, BOX, TextAlign.CENTER);

            var landed = TextGeometry.caretAt(paragraph, layout, bottom, BOX, TextAlign.CENTER)
                    .x();
            assertTrue(
                    Math.abs(landed - column) < font.size(),
                    "two lines down, the caret is " + Math.abs(landed - column) + " from the column it started in");
        }

        @Test
        @DisplayName("a selection is shifted with the glyphs it covers")
        void selectionFollowsTheAlignment() {
            var paragraph = paragraph("hello");
            var layout = paragraph.layout(BOX);
            var indent = TextAlign.CENTER.indentOf(layout.lines().getFirst().width(), BOX);

            var plain = TextGeometry.selectionRects(paragraph, layout, 1, 4).getFirst();
            var centred = TextGeometry.selectionRects(paragraph, layout, 1, 4, BOX, TextAlign.CENTER)
                    .getFirst();

            assertEquals(plain.left() + indent, centred.left(), 0.001);
            assertEquals(plain.width(), centred.width(), 0.001, "a highlight moves; it does not stretch");
        }

        /// The two cases [TextAlign#indentOf] clamps, from the geometry's side: a
        /// measurement has no box to align in, and a line wider than its box is
        /// not dragged off the front of it.
        @Test
        @DisplayName("an unconstrained width and an overlong line align to nothing")
        void nothingToAlignIn() {
            var paragraph = paragraph("hello");
            var layout = paragraph.layout(Paragraph.UNCONSTRAINED);
            var width = layout.lines().getFirst().width();

            assertEquals(
                    TextGeometry.caretAt(paragraph, layout, 3).x(),
                    TextGeometry.caretAt(paragraph, layout, 3, Paragraph.UNCONSTRAINED, TextAlign.CENTER)
                            .x(),
                    0.001,
                    "an infinite box would give an infinite indent");
            assertEquals(
                    TextGeometry.caretAt(paragraph, layout, 3).x(),
                    TextGeometry.caretAt(paragraph, layout, 3, width / 2, TextAlign.END)
                            .x(),
                    0.001,
                    "a line wider than its box starts at the leading edge");
        }

        @Test
        @DisplayName("an alignment is required rather than defaulted")
        void alignmentMayNotBeNull() {
            var paragraph = paragraph("hello");
            var layout = paragraph.layout(BOX);

            assertThrows(NullPointerException.class, () -> TextGeometry.caretAt(paragraph, layout, 0, BOX, null));
            assertThrows(NullPointerException.class, () -> TextGeometry.offsetAt(paragraph, layout, 0, 0, BOX, null));
            assertThrows(
                    NullPointerException.class,
                    () -> TextGeometry.moveLine(paragraph, layout, 0, 1, Double.NaN, BOX, null));
            assertThrows(
                    NullPointerException.class, () -> TextGeometry.selectionRects(paragraph, layout, 0, 1, BOX, null));
        }
    }
}
