package io.github.digitalsmile.goldberry.text;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.digitalsmile.goldberry.RendererRequirement;
import io.github.digitalsmile.goldberry.assets.BundledFont;
import io.github.digitalsmile.goldberry.natives.yoga.MeasureMode;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/// Wrapping, and the number a measure function reports.
///
/// The assertions are about *consistency between the two answers*: what a line
/// claims to be wide, what the paragraph claims to be tall, and what a caller
/// measuring the same text directly gets. Those three disagreeing is the failure
/// mode a layout engine cannot see — Yoga believes the measure function.
class ParagraphTest {

    private Font font;

    @BeforeEach
    void openFont() {
        RendererRequirement.enforce();
        font = Font.bundled(BundledFont.UI, 16);
    }

    @AfterEach
    void closeFont() {
        if (font != null) {
            font.close();
        }
    }

    @Test
    @DisplayName("text that fits is one line as wide as the text")
    void shortTextIsOneLine() {
        var paragraph = Paragraph.of(font, "Goldberry");
        var layout = paragraph.layout(1000);

        assertEquals(1, layout.lineCount());
        // The line's width is the text's width, not the width it was offered.
        // Reporting the offered width would make every label claim its whole
        // column, and a centred parent would centre the empty space.
        assertEquals(font.widthOf("Goldberry"), layout.width(), 0.01);
        assertEquals(font.lineHeight(), layout.height(), 0.01);
    }

    @Test
    @DisplayName("a narrower width means more lines and a taller paragraph")
    void narrowerWrapsMore() {
        var text = "Yoga laid this out, HarfBuzz shaped it, and Blend2D drew every glyph of it.";
        var paragraph = Paragraph.of(font, text);

        var wide = paragraph.layout(600);
        var narrow = paragraph.layout(150);

        assertTrue(narrow.lineCount() > wide.lineCount(),
                () -> narrow.lineCount() + " lines at 150 against " + wide.lineCount() + " at 600");
        assertTrue(narrow.height() > wide.height(), "taller, by exactly the extra lines");
        assertEquals(narrow.lineCount() * font.lineHeight(), narrow.height(), 0.01);
    }

    @Test
    @DisplayName("no line is wider than the width it was wrapped to")
    void linesFit() {
        var text = "The quick brown fox jumps over the lazy dog, and then does it again for luck.";
        var paragraph = Paragraph.of(font, text);

        for (var width : new double[] {80, 120, 200, 340, 500}) {
            var layout = paragraph.layout(width);
            for (var line : layout.lines()) {
                // The one exception is a single word wider than the line, and
                // none of these words is: at 80 points the longest word still
                // fits, which the assertion below confirms by holding.
                assertTrue(line.width() <= width + 0.01,
                        () -> "a line of " + line.width() + " does not fit in " + width);
            }
            assertTrue(layout.width() <= width + 0.01, "and neither does the paragraph");
        }
    }

    @Test
    @DisplayName("every character survives the wrap, in order")
    void wrappingLosesNothing() {
        var text = "The quick brown fox jumps over the lazy dog";
        var paragraph = Paragraph.of(font, text);
        var layout = paragraph.layout(90);

        assertTrue(layout.lineCount() > 1, "this needs to have actually wrapped");

        var rebuilt = new StringBuilder();
        var expectedStart = 0;
        for (var line : layout.lines()) {
            // Contiguous: a gap would mean characters silently dropped, which on
            // ordinary prose reads as a typo rather than as a bug.
            assertEquals(expectedStart, line.start(), "lines must be contiguous");
            rebuilt.append(line.textIn(text));
            expectedStart = line.end();
        }
        assertEquals(text.length(), expectedStart, "the last line reaches the end");
        assertEquals(text, rebuilt.toString());
    }

    @Test
    @DisplayName("a trailing space is in the line but not in its width")
    void trailingSpaceDoesNotWiden() {
        var paragraph = Paragraph.of(font, "one two");
        var layout = paragraph.layout(font.widthOf("one ") + 1);

        assertEquals(2, layout.lineCount());
        var first = layout.lines().getFirst();

        assertEquals("one ", first.textIn("one two"), "the space belongs to the line it ended");
        // ...and not to its width, or a centred line would sit a space to the
        // left of where a reader expects it.
        assertEquals(font.widthOf("one"), first.width(), 0.01);
    }

    @Test
    @DisplayName("a word wider than the line overflows rather than being cut")
    void overlongWordsOverflow() {
        var word = "Donaudampfschifffahrtsgesellschaftskapitaen";
        var paragraph = Paragraph.of(font, word);
        var layout = paragraph.layout(20);

        // Breaking inside it would be hyphenation, which is a style's decision.
        // Dropping it would be worse. So it overflows, visibly and on purpose.
        assertEquals(1, layout.lineCount());
        assertTrue(layout.width() > 20, () -> "it should overflow, and it is " + layout.width());
        assertEquals(word, layout.lines().getFirst().textIn(word));
    }

    @Test
    @DisplayName("a newline breaks a line even where the width would not")
    void newlinesAreHonoured() {
        var paragraph = Paragraph.of(font, "one\ntwo\nthree");
        var layout = paragraph.layout(Paragraph.UNCONSTRAINED);

        // Unconstrained: nothing here would wrap on width, so three lines can
        // only be the newlines. BreakIterator offers a break after a newline but
        // does not say it is mandatory, which is why they are handled first.
        assertEquals(3, layout.lineCount());
        assertEquals("two", layout.lines().get(1).textIn("one\ntwo\nthree").strip());
    }

    @Test
    @DisplayName("a blank line draws nothing and still takes a line's height")
    void blankLinesTakeSpace() {
        var paragraph = Paragraph.of(font, "one\n\ntwo");
        var layout = paragraph.layout(Paragraph.UNCONSTRAINED);

        assertEquals(3, layout.lineCount());
        assertTrue(layout.lines().get(1).isEmpty(), "nothing to draw");
        assertEquals(3 * font.lineHeight(), layout.height(), 0.01, "and still three lines tall");
    }

    @Test
    @DisplayName("empty text is a paragraph with one empty line")
    void emptyTextIsOneEmptyLine() {
        var paragraph = Paragraph.of(font, "");
        var layout = paragraph.layout(100);

        // Not zero lines: an empty text-input is still one line tall, and a
        // caret has to sit somewhere.
        assertEquals(1, layout.lineCount());
        assertEquals(0.0, layout.width());
        assertEquals(font.lineHeight(), layout.height(), 0.01);
    }

    @Test
    @DisplayName("re-wrapping at the same width returns the same layout, not a new one")
    void theSameWidthIsMemoised() {
        var paragraph = Paragraph.of(font, "Yoga proposes a width, and this answers with a height.");

        var first = paragraph.layout(200);
        var same = paragraph.layout(200);
        // Identity, not equality: a measure callback runs from inside C several
        // times per layout pass, and the paint that follows asks once more. The
        // memo is what keeps that from re-breaking the paragraph each time.
        assertSame(first, same);

        var different = paragraph.layout(120);
        assertNotEquals(first.lineCount(), different.lineCount());
        assertSame(different, paragraph.layout(120));
        // And going back re-computes rather than returning stale lines.
        assertEquals(first.lineCount(), paragraph.layout(200).lineCount());
    }

    @Test
    @DisplayName("the measure function reports what the layout says")
    void measureFunctionAgreesWithLayout() {
        var text = "Yoga cannot see inside a leaf, so it asks how tall the content came out.";
        var paragraph = Paragraph.of(font, text);
        var measure = paragraph.measureFunction();

        var atMost = measure.measure(200, MeasureMode.AT_MOST, Float.NaN, MeasureMode.UNDEFINED);
        var layout = paragraph.layout(200);

        assertEquals(layout.width(), atMost.width(), 0.01);
        assertEquals(layout.height(), atMost.height(), 0.01);
        assertTrue(atMost.width() <= 200, "AT_MOST is an upper bound");
    }

    @Test
    @DisplayName("EXACTLY takes the width it was given, not the width it used")
    void exactlyReportsTheGivenWidth() {
        var paragraph = Paragraph.of(font, "short");
        var measure = paragraph.measureFunction();

        var exactly = measure.measure(300, MeasureMode.EXACTLY, Float.NaN, MeasureMode.UNDEFINED);

        // The parent has already decided; reporting anything else would have
        // Yoga resolve a size the parent then overrides, and the text would be
        // laid out against a width it is not drawn in.
        assertEquals(300f, exactly.width());
        assertEquals(font.lineHeight(), exactly.height(), 0.01);
    }

    @Test
    @DisplayName("UNDEFINED means no constraint, and the width arrives as NaN")
    void undefinedDoesNotWrap() {
        var text = "This is long enough that any real constraint would wrap it somewhere.";
        var paragraph = Paragraph.of(font, text);
        var measure = paragraph.measureFunction();

        // Yoga passes NaN with UNDEFINED. Reading it as a width would wrap every
        // line to nothing, since no comparison against NaN is ever true.
        var free = measure.measure(Float.NaN, MeasureMode.UNDEFINED, Float.NaN, MeasureMode.UNDEFINED);

        assertEquals(font.lineHeight(), free.height(), 0.01, "one line");
        assertEquals(font.widthOf(text), free.width(), 0.01);
    }

    @Test
    @DisplayName("a NaN width is refused rather than wrapping everything to nothing")
    void nanWidthIsRefused() {
        var paragraph = Paragraph.of(font, "Goldberry");
        assertThrows(IllegalArgumentException.class, () -> paragraph.layout(Double.NaN));
    }

    @Test
    @DisplayName("right-to-left text is refused at construction, not mis-wrapped")
    void rightToLeftIsRefused() {
        // HarfBuzz returns these glyphs in visual order, so accumulating prefix
        // widths in logical order would measure the wrong glyphs -- and produce
        // a paragraph that wraps confidently in the wrong places. Loud beats
        // silent until bidi run splitting exists.
        var arabic = "مرحبا بالعالم";
        var thrown = assertThrows(
                UnsupportedOperationException.class, () -> Paragraph.of(font, arabic));
        assertTrue(thrown.getMessage().contains("right-to-left"), thrown.getMessage());
    }

    @Test
    @DisplayName("the glyph ranges index the one run the paragraph was shaped into")
    void linesIndexTheSingleRun() {
        var text = "The quick brown fox jumps over the lazy dog";
        var paragraph = Paragraph.of(font, text);
        var layout = paragraph.layout(90);

        assertTrue(layout.lineCount() > 1);
        var previousEnd = 0;
        for (var line : layout.lines()) {
            assertTrue(line.glyphStart() >= previousEnd, "glyph ranges advance");
            assertTrue(line.glyphEnd() <= paragraph.glyphs().length(), "and stay inside the run");
            previousEnd = line.glyphEnd();
        }
        // Wrapping produced ranges over glyphs that were shaped exactly once --
        // which is what makes re-wrapping free, and what the measure callback
        // depends on to be affordable.
        assertEquals(text.length(), paragraph.text().length());
    }
}
