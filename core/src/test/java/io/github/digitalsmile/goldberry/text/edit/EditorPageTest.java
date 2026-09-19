package io.github.digitalsmile.goldberry.text.edit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import io.github.digitalsmile.goldberry.RendererRequirement;
import io.github.digitalsmile.goldberry.assets.BundledFont;
import io.github.digitalsmile.goldberry.input.event.KeyEvent;
import io.github.digitalsmile.goldberry.input.key.Key;
import io.github.digitalsmile.goldberry.input.key.Modifiers;
import io.github.digitalsmile.goldberry.text.font.Font;

/// What a page is — [ADR-0410].
///
/// `PageUp` and `PageDown` used to move ten lines whatever the caller had drawn,
/// because a page is the height of a viewport and an editor on a canvas has none.
/// Now a caller can say how tall its viewport is, and the tests here are about the
/// two halves of that: a declared height is obeyed in **visual** lines, and a
/// caller that says nothing still gets ten.
class EditorPageTest {

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

    /// Forty numbered lines, so the line a caret landed on is the number on it.
    private static String lines(int count) {
        var out = new StringBuilder();
        for (var i = 0; i < count; i++) {
            out.append("line ").append(i).append('\n');
        }
        return out.toString();
    }

    private Editor editor() {
        return new Editor(font).multiline(true).text(lines(40)).caretTo(0, false);
    }

    private static KeyEvent key(Key which) {
        return new KeyEvent(KeyEvent.Kind.PRESSED, which, Modifiers.NONE, false, null);
    }

    private static KeyEvent key(Key which, Modifiers modifiers) {
        return new KeyEvent(KeyEvent.Kind.PRESSED, which, modifiers, false, null);
    }

    private static final Modifiers SHIFT = new Modifiers(true, false, false, false);

    @Nested
    @DisplayName("a caller that says how tall its viewport is")
    class Declared {

        @Test
        @DisplayName("a page is the lines the viewport holds")
        void pageIsTheViewport() {
            var editor = editor().viewportHeight(5 * font.lineHeight());

            assertTrue(editor.onKey(key(Key.PAGE_DOWN)));
            assertEquals(5, editor.caret().line(), "five lines on screen is a five-line page");

            assertTrue(editor.onKey(key(Key.PAGE_DOWN)));
            assertEquals(10, editor.caret().line());

            assertTrue(editor.onKey(key(Key.PAGE_UP)));
            assertEquals(5, editor.caret().line(), "and back up by the same amount");
        }

        /// The **whole** lines it holds: half a row of text is not a row anybody
        /// can read, and paging onto it would leave the caret under the edge.
        @Test
        @DisplayName("a part line at the bottom is not part of the page")
        void partLinesDoNotCount() {
            var editor = editor().viewportHeight(5.75 * font.lineHeight());

            editor.onKey(key(Key.PAGE_DOWN));

            assertEquals(5, editor.caret().line());
        }

        /// A viewport too short for one line is a caller that has told us
        /// something, so the key still moves: a `PageDown` that reports `true` and
        /// does nothing is worse than one that moves a line.
        @Test
        @DisplayName("a viewport shorter than a line still pages by one")
        void neverLessThanOneLine() {
            var editor = editor().viewportHeight(font.lineHeight() / 4);

            editor.onKey(key(Key.PAGE_DOWN));

            assertEquals(1, editor.caret().line());
        }

        /// The count is in visual lines, which is what "on screen" means: with the
        /// text wrapped, a four-line viewport is four *rows* and not four
        /// paragraphs.
        @Test
        @DisplayName("the lines are the ones that were wrapped, not the ones that were typed")
        void visualLinesRatherThanHard() {
            var editor = new Editor(font)
                    .multiline(true)
                    .text("aaa bbb\nccc ddd\neee fff\nggg hhh\niii jjj\n")
                    .wrapWidth(font.widthOf("aaa "))
                    .caretTo(0, false)
                    .viewportHeight(4 * font.lineHeight());

            assertTrue(editor.lines().size() > 5, "every hard line wrapped, or this test proves nothing");

            editor.onKey(key(Key.PAGE_DOWN));

            assertEquals(4, editor.caret().line());
            assertEquals(2, editor.lines().hardLineOf(4), "four rows down is two typed lines down");
        }

        @Test
        @DisplayName("Shift+PageDown extends the selection by a page")
        void shiftExtends() {
            var editor = editor().viewportHeight(6 * font.lineHeight());

            assertTrue(editor.onKey(key(Key.PAGE_DOWN, SHIFT)));

            assertEquals(0, editor.edit().anchor(), "the anchor stayed where the caret started");
            assertEquals(6, editor.caret().line());
            assertTrue(editor.edit().selectedText().startsWith("line 0\n"));
        }

        @Test
        @DisplayName("a page past the end is the end of the text")
        void offTheEnd() {
            var editor = editor().viewportHeight(30 * font.lineHeight());

            editor.onKey(key(Key.PAGE_DOWN));
            assertEquals(30, editor.caret().line());

            editor.onKey(key(Key.PAGE_DOWN));
            assertEquals(editor.text().length(), editor.edit().caret(), "the end, rather than nothing happening");
        }

        /// How tall the viewport is changes what one key means and nothing else —
        /// so the wrap, and the shaping under it, survive being told.
        @Test
        @DisplayName("declaring a viewport re-wraps nothing")
        void nothingIsInvalidated() {
            var editor = editor().wrapWidth(120);
            var before = editor.lines();
            var shaped = editor.document();

            editor.viewportHeight(8 * font.lineHeight());

            assertSame(shaped, editor.document(), "a height is not a shaping");
            assertSame(before, editor.lines(), "nor a wrap");
        }
    }

    @Nested
    @DisplayName("a caller that says nothing")
    class Default {

        @Test
        @DisplayName("a page is ten lines, as it always was")
        void tenLines() {
            var editor = editor();

            assertTrue(Double.isNaN(editor.viewportHeight()), "nothing was said");
            editor.onKey(key(Key.PAGE_DOWN));

            assertEquals(10, editor.caret().line());
        }

        /// The three ways of saying "I do not know how tall I am", all of which
        /// have to mean the fallback rather than a page of nothing.
        @Test
        @DisplayName("NaN, zero and a negative height are all a caller that said nothing")
        void unsaidValues() {
            for (var height : new double[] {Double.NaN, 0, -40}) {
                var editor = editor().viewportHeight(height);

                editor.onKey(key(Key.PAGE_DOWN));

                assertEquals(10, editor.caret().line(), "height " + height);
            }
        }

        /// A single-line editor wraps rather than scrolling, and the page keys
        /// still move by lines there — `EditSurface.WRAPPED` — so the fallback
        /// applies to it as well.
        @Test
        @DisplayName("a wrapped single-line editor pages by its viewport too")
        void wrappedSingleLine() {
            var editor = new Editor(font)
                    .text("one two three four five six seven eight nine ten eleven twelve thirteen")
                    .wrapWidth(font.widthOf("one two "))
                    .caretTo(0, false)
                    .viewportHeight(2 * font.lineHeight());

            assertTrue(editor.onKey(key(Key.PAGE_DOWN)));

            assertEquals(2, editor.caret().line());
        }
    }
}
