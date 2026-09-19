package io.github.digitalsmile.goldberry.text.edit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import io.github.digitalsmile.goldberry.RendererRequirement;
import io.github.digitalsmile.goldberry.assets.BundledFont;
import io.github.digitalsmile.goldberry.text.Paragraph;
import io.github.digitalsmile.goldberry.text.document.TextDocument;
import io.github.digitalsmile.goldberry.text.flow.TextAlign;
import io.github.digitalsmile.goldberry.text.font.Font;

/// What one keystroke into a long text costs an [Editor], **counted** —
/// [ADR-0411].
///
/// The guard for a cost rather than for a feature, so the assertions here are
/// counts and none of them is a duration: how many characters a keystroke shaped
/// is the same number on every machine, and a millisecond threshold that passes on
/// a workstation and fails on a loaded runner teaches nobody anything.
/// `EditorKeystrokeBenchmark` is where the milliseconds are, and it asserts
/// nothing.
///
/// The rule all of it is about, which is `TextAreaKeystrokeCostTest`'s rule one
/// layer down: **a keystroke into a long text must cost what a keystroke into a
/// short one costs.** Before this an `Editor` held one [Paragraph] over everything
/// it was given, so it re-shaped the whole text on every key — and every counter
/// in the toolkit said it had shaped exactly one paragraph.
///
/// The second half is [Agreement]: a caret, a hit test and a selection measured
/// against a text shaped a hard line at a time land where the whole-text shaping
/// put them. That is the part a user would notice.
class EditorDocumentTest {

    private static final int SHORT = 2_000;

    private static final int LONG = 500_000;

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

    /// A text of about `size` characters, in numbered lines.
    ///
    /// Every line different, which is not decoration: a shaper is free to cache by
    /// the string, and a text made of one repeated line would shape once and hit
    /// for ever after — flattering exactly the number under test.
    private static String note(int size) {
        var out = new StringBuilder(size + 128);
        var line = 1;
        while (out.length() < size) {
            out.append("Line ")
                    .append(line++)
                    .append(": an editor on a canvas holds a sticky, a label or a whole document,")
                    .append(" and the question is what one keystroke into it costs.\n");
        }
        return out.toString();
    }

    /// A shaper that says how much work it was asked for.
    private static final class Counting implements TextDocument.Shaper {

        private final Font font;

        private int lines;

        private int characters;

        Counting(Font font) {
            this.font = font;
        }

        @Override
        public Paragraph shape(String line) {
            lines++;
            characters += line.length();
            return Paragraph.of(font, line);
        }

        void reset() {
            lines = 0;
            characters = 0;
        }
    }

    /// An editor over `size` characters, already shaped once, with the caret in the
    /// middle of it and the counter back at zero.
    private Editor opened(Counting counting, int size) {
        var editor = new Editor(font).shaper(counting).multiline(true).text(note(size));
        editor.caretTo(editor.text().length() / 2, false);
        editor.document();
        counting.reset();
        return editor;
    }

    @Nested
    @DisplayName("what a keystroke shapes")
    class Cost {

        @Test
        @DisplayName("a keystroke shapes the line it landed on, whatever the text's size")
        void aKeystrokeShapesOneLine() {
            var shortShaper = new Counting(font);
            var shortEditor = opened(shortShaper, SHORT);
            var longShaper = new Counting(font);
            var longEditor = opened(longShaper, LONG);

            shortEditor.onText("x");
            shortEditor.caret();
            longEditor.onText("x");
            longEditor.caret();

            assertEquals(1, shortShaper.lines, "one hard line was re-shaped");
            assertEquals(1, longShaper.lines, "and one, in a text two hundred and fifty times longer");
            assertTrue(
                    longShaper.characters < 200,
                    "a keystroke into half a megabyte shaped " + longShaper.characters + " characters");
            // Comparable rather than equal, and the difference is the line
            // *number*: "Line 3521" is four characters longer than "Line 9", which
            // is the whole of what a two-hundred-and-fifty-fold longer text costs
            // a keystroke.
            assertTrue(
                    Math.abs(longShaper.characters - shortShaper.characters) < 10,
                    "a keystroke shaped " + shortShaper.characters + " characters in a " + SHORT
                            + "-character text and " + longShaper.characters + " in a " + LONG + "-character one");
        }

        /// The cost that is irreducible here and is stated rather than hidden:
        /// where every line breaks and how tall the text is are facts about every
        /// line, and nothing knows a line's height without shaping it. What
        /// changed is that it is paid once per text instead of once per keystroke.
        @Test
        @DisplayName("opening shapes everything, once")
        void openingShapesTheText() {
            var counting = new Counting(font);
            var text = note(SHORT);
            var editor = new Editor(font).shaper(counting).multiline(true).text(text);

            editor.document();

            var newlines = (int) text.chars().filter(c -> c == '\n').count();
            assertEquals(text.length() - newlines, counting.characters, "every character except the breaks");
            assertEquals(newlines + 1, counting.lines);

            counting.reset();
            assertSame(editor.document(), editor.document(), "asking twice does not reshape");
            assertEquals(0, counting.characters);
        }

        @Test
        @DisplayName("every line the keystroke did not touch keeps the paragraph it had")
        void otherLinesAreReused() {
            var counting = new Counting(font);
            var editor = opened(counting, SHORT);
            var before = editor.document();
            var touched = before.hardLineAt(editor.edit().caret());

            editor.onText("x");
            var after = editor.document();

            assertNotSame(before, after, "the text changed, so the document did");
            assertEquals(before.hardLineCount(), after.hardLineCount(), "and no line was added");
            for (var k = 0; k < before.hardLineCount(); k++) {
                if (k == touched) {
                    assertNotSame(before.paragraphOf(k), after.paragraphOf(k), "the line typed into was re-shaped");
                } else {
                    assertSame(before.paragraphOf(k), after.paragraphOf(k), "line " + k + " was not");
                }
            }
        }

        /// A width decides where lines break and not what the glyphs are. The old
        /// editor threw its layout away on a width change and kept the shaping;
        /// this keeps both, because the break is memoised per line.
        @Test
        @DisplayName("a resize re-wraps and shapes nothing")
        void resizeShapesNothing() {
            var counting = new Counting(font);
            var editor = opened(counting, SHORT);

            editor.wrapWidth(300);
            var narrow = editor.lines().size();
            editor.wrapWidth(600);
            var wide = editor.lines().size();

            assertTrue(narrow > wide, "a narrower box wraps into more rows");
            assertEquals(0, counting.characters, "and not one character was re-shaped to find that out");
        }

        /// A selection is drawn per visual line, and the lines it is *not* on are
        /// not the caller's business to walk: a highlight over three rows of a
        /// ten-thousand-row text used to cost a walk over all of them, every frame.
        @Test
        @DisplayName("a selection asks about the rows it covers and no others")
        void selectionIsLocal() {
            var counting = new Counting(font);
            var editor = opened(counting, LONG);
            var at = editor.edit().caret();
            editor.caretTo(at, false);
            editor.caretTo(at + 20, true);

            var rects = editor.selectionRects();

            assertTrue(rects.size() <= 2, "twenty characters cover one row, or two if a wrap falls inside them");
            assertEquals(0, counting.characters, "and measuring them shaped nothing");
        }
    }

    @Nested
    @DisplayName("a document measures where a paragraph did")
    class Agreement {

        /// One logical unit of slack, which is a tenth of a character: the two
        /// shapings are the same glyphs in the same order, but a whole-text
        /// paragraph accumulates its prefix widths through the line breaks and this
        /// one starts each line at zero.
        private static final double SLACK = 1;

        private static final String TEXT = """
                The quick brown fox jumps over the lazy dog, and then does it again \
                because one sentence is not a wrap.
                A second line, shorter.

                And a fourth after an empty one.""";

        private static final double WIDTH = 220;

        /// The same text, the old way: one paragraph over everything.
        private Paragraph whole() {
            return Paragraph.of(font, TEXT);
        }

        private Editor editor(TextAlign align) {
            return new Editor(font)
                    .multiline(true)
                    .wrapWidth(WIDTH)
                    .textAlign(align)
                    .text(TEXT);
        }

        @Test
        @DisplayName("the same text wraps into the same rows")
        void sameRows() {
            var editor = editor(TextAlign.START);
            var paragraph = whole();
            var layout = paragraph.layout(WIDTH);

            assertEquals(layout.lineCount(), editor.lines().size());
            for (var i = 0; i < layout.lineCount(); i++) {
                assertEquals(
                        layout.lines().get(i).start(), editor.lines().get(i).start(), "row " + i + " starts");
                assertEquals(layout.lines().get(i).end(), editor.lines().get(i).end(), "row " + i + " ends");
            }
        }

        @Test
        @DisplayName("the caret lands where the whole-text shaping put it")
        void caretsAgree() {
            for (var align : TextAlign.values()) {
                var editor = editor(align);
                var paragraph = whole();
                var layout = paragraph.layout(WIDTH);

                for (var offset = 0; offset <= TEXT.length(); offset += 7) {
                    var expected = TextGeometry.caretAt(paragraph, layout, offset, WIDTH, align);
                    editor.caretTo(offset, false);
                    var actual = editor.caret();

                    var where = align + " at " + offset;
                    assertEquals(expected.line(), actual.line(), where + ": the row");
                    assertEquals(expected.x(), actual.x(), SLACK, where + ": the column");
                    assertEquals(expected.top(), actual.top(), 0.001, where + ": the top");
                    assertEquals(expected.height(), actual.height(), 0.001, where + ": the height");
                }
            }
        }

        @Test
        @DisplayName("a press lands on the offset it used to")
        void pressesAgree() {
            for (var align : TextAlign.values()) {
                var editor = editor(align);
                var paragraph = whole();
                var layout = paragraph.layout(WIDTH);
                var lineHeight = font.lineHeight();

                for (var row = 0; row < layout.lineCount(); row++) {
                    for (var x = 0.0; x < WIDTH; x += 37) {
                        var y = row * lineHeight + lineHeight / 2;
                        var expected = TextGeometry.offsetAt(paragraph, layout, x, y, WIDTH, align);

                        editor.pointerAt(x, y, false, 1);

                        assertEquals(expected, editor.edit().caret(), align + " at (" + x + ", " + y + ")");
                    }
                }
            }
        }

        @Test
        @DisplayName("a selection that spans wraps and a newline draws the same rectangles")
        void selectionsAgree() {
            var editor = editor(TextAlign.START);
            var paragraph = whole();
            var layout = paragraph.layout(WIDTH);
            var start = 20;
            var end = TEXT.length() - 10;

            var expected = TextGeometry.selectionRects(paragraph, layout, start, end, WIDTH, TextAlign.START);
            editor.caretTo(start, false);
            editor.caretTo(end, true);
            var actual = editor.selectionRects();

            assertEquals(expected.size(), actual.size(), "one rectangle per row either way");
            for (var i = 0; i < expected.size(); i++) {
                assertEquals(expected.get(i).left(), actual.get(i).left(), SLACK, "rect " + i + " left");
                assertEquals(expected.get(i).top(), actual.get(i).top(), 0.001, "rect " + i + " top");
                assertEquals(expected.get(i).width(), actual.get(i).width(), SLACK, "rect " + i + " width");
                assertEquals(expected.get(i).height(), actual.get(i).height(), 0.001, "rect " + i + " height");
            }
        }

        @Test
        @DisplayName("Down through a short line keeps the column it started in")
        void verticalAgrees() {
            var editor = editor(TextAlign.START);
            var paragraph = whole();
            var layout = paragraph.layout(WIDTH);

            for (var offset : new int[] {5, 40, 80, TEXT.indexOf("A second")}) {
                var expected = TextGeometry.moveLine(paragraph, layout, offset, 2, Double.NaN, WIDTH, TextAlign.START);
                var actual = TextGeometry.moveLine(
                        editor.document(), editor.lines(), offset, 2, Double.NaN, WIDTH, TextAlign.START);

                assertEquals(expected, actual, "two rows down from " + offset);
            }
        }
    }
}
