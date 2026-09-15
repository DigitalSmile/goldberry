package io.github.digitalsmile.goldberry.text.edit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
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
import io.github.digitalsmile.goldberry.render.Clipboard;
import io.github.digitalsmile.goldberry.text.flow.TextAlign;
import io.github.digitalsmile.goldberry.text.font.Font;

/// A text editor with no widget around it — ADR-0285.
///
/// [TextEditTest] covers what an edit *is* and [TextGeometryTest] covers where a
/// caret lands. What is left, and what this is about, is the **wiring**: which key
/// does what, when an undo step begins and ends, and that a shaped paragraph is
/// not measured against one wrap width and drawn against another.
class EditorTest {

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

    private Editor editor(String text) {
        return new Editor(font).text(text);
    }

    private static KeyEvent key(Key which) {
        return new KeyEvent(KeyEvent.Kind.PRESSED, which, Modifiers.NONE, false, null);
    }

    private static KeyEvent key(Key which, Modifiers modifiers) {
        return new KeyEvent(KeyEvent.Kind.PRESSED, which, modifiers, false, null);
    }

    /// A clipboard with nothing under it, so a test can paste without a platform.
    private static final class Board implements Clipboard {

        private String held = "";

        @Override
        public boolean hasText() {
            return !held.isEmpty();
        }

        @Override
        public String text() {
            return held;
        }

        @Override
        public boolean text(String text) {
            held = text;
            return true;
        }
    }

    @Nested
    @DisplayName("typing and deleting")
    class Editing {

        @Test
        @DisplayName("committed text is inserted at the caret")
        void insertsText() {
            var editor = editor("ac");
            editor.caretTo(1, false);

            assertTrue(editor.onText("b"));
            assertEquals("abc", editor.text());
            assertEquals(2, editor.edit().caret());
        }

        @Test
        @DisplayName("a key this does not handle is left alone")
        void leavesOtherKeysAlone() {
            var editor = editor("hi");

            // The contract a canvas depends on: an unhandled key is not consumed,
            // so Tab still moves focus and Escape still closes what it closes.
            assertFalse(editor.onKey(key(Key.TAB)));
            assertFalse(editor.onKey(key(Key.ESCAPE)));
            assertFalse(editor.onKey(key(Key.F1)));
        }

        @Test
        @DisplayName("Enter is a newline only when the editor is multiline")
        void enterDependsOnTheMode() {
            var single = editor("hi");
            assertFalse(single.onKey(key(Key.ENTER)), "a single-line editor lets Enter through, so a form can submit");
            assertEquals("hi", single.text());

            var multi = editor("hi").multiline(true);
            multi.caretTo(2, false);
            assertTrue(multi.onKey(key(Key.ENTER)));
            assertEquals("hi\n", multi.text());
        }

        @Test
        @DisplayName("Backspace and Delete go by character, or by word with Ctrl")
        void deletes() {
            var editor = editor("one two");
            editor.caretTo(7, false);

            assertTrue(editor.onKey(key(Key.BACKSPACE)));
            assertEquals("one tw", editor.text());

            assertTrue(editor.onKey(key(Key.BACKSPACE, new Modifiers(false, true, false, false))));
            assertEquals("one ", editor.text());
        }

        @Test
        @DisplayName("a read-only editor moves and selects but does not change")
        void readOnlyRefusesEdits() {
            var editor = editor("hello").readOnly(true);
            // `text(...)` leaves the caret at the end, which is where a document
            // that was just loaded should open.
            editor.caretTo(0, false);

            assertFalse(editor.onText("x"));
            assertFalse(editor.onKey(key(Key.BACKSPACE)));
            assertEquals("hello", editor.text());

            assertTrue(editor.onKey(key(Key.RIGHT)), "movement still works, which is what read-only means");
            assertEquals(1, editor.edit().caret());
        }
    }

    @Nested
    @DisplayName("undo")
    class Undo {

        @Test
        @DisplayName("a run of typing undoes as one step")
        void foldsATypingRun() {
            var editor = editor("");
            editor.onText("h");
            editor.onText("i");
            editor.onText("!");

            assertTrue(editor.undo());
            assertEquals("", editor.text(), "three keystrokes, one step");
            assertTrue(editor.redo());
            assertEquals("hi!", editor.text());
        }

        @Test
        @DisplayName("moving the caret ends the run")
        void movementBreaksTheRun() {
            var editor = editor("");
            editor.onText("one");
            editor.onKey(key(Key.LEFT));
            editor.onText("X");

            assertTrue(editor.undo());
            assertEquals("one", editor.text(), "the X went back and the word did not");
        }

        @Test
        @DisplayName("Ctrl+Z and Ctrl+Shift+Z are undo and redo")
        void theAccelerators() {
            var control = new Modifiers(false, true, false, false);
            var controlShift = new Modifiers(true, true, false, false);

            var editor = editor("");
            editor.onText("typed");

            assertTrue(editor.onKey(key(Key.Z, control)));
            assertEquals("", editor.text());
            assertTrue(editor.onKey(key(Key.Z, controlShift)));
            assertEquals("typed", editor.text());
            assertTrue(editor.onKey(key(Key.Z, control)));
            assertTrue(editor.onKey(key(Key.Y, control)), "and Ctrl+Y is redo as well");
            assertEquals("typed", editor.text());
        }

        @Test
        @DisplayName("replacing the text clears the history")
        void loadingClearsTheHistory() {
            var editor = editor("");
            editor.onText("typed");

            editor.text("a different document");

            assertFalse(editor.canUndo(), "an undo that reached past a load would restore another document's text");
            assertFalse(editor.undo());
        }

        @Test
        @DisplayName("nothing to undo is false rather than a no-op that consumed a key")
        void reportsNothingToUndo() {
            var editor = editor("hello");
            assertFalse(editor.canUndo());
            assertFalse(editor.undo());
            assertFalse(editor.redo());
        }
    }

    @Nested
    @DisplayName("selection, movement and the clipboard")
    class Selecting {

        @Test
        @DisplayName("Shift extends the selection and a plain arrow collapses it")
        void extendsWithShift() {
            var shift = new Modifiers(true, false, false, false);
            var editor = editor("hello");
            editor.caretTo(0, false);

            editor.onKey(key(Key.RIGHT, shift));
            editor.onKey(key(Key.RIGHT, shift));
            assertEquals("he", editor.edit().selectedText());

            editor.onKey(key(Key.RIGHT));
            assertFalse(editor.edit().hasSelection());
        }

        @Test
        @DisplayName("Home and End go to the ends of the visual line, not of the text")
        void homeAndEndAreVisual() {
            var editor = editor("one\ntwo").multiline(true);
            editor.caretTo(5, false);

            assertTrue(editor.onKey(key(Key.HOME)));
            assertEquals(4, editor.edit().caret(), "the start of the second line");

            assertTrue(editor.onKey(key(Key.END)));
            assertEquals(7, editor.edit().caret());

            var control = new Modifiers(false, true, false, false);
            assertTrue(editor.onKey(key(Key.HOME, control)));
            assertEquals(0, editor.edit().caret(), "Ctrl+Home is the start of everything");
        }

        @Test
        @DisplayName("a double click selects a word and a triple click selects the lot")
        void clickCounts() {
            var editor = editor("one two three");
            var caret = editor.caret();

            // Click where the caret would be at offset 5, which is inside "two".
            var x = TextGeometry.caretAt(editor.paragraph(), editor.layout(), 5).x();
            editor.pointerAt(x, caret.top(), false, 2);
            assertEquals("two", editor.edit().selectedText());

            editor.pointerAt(x, caret.top(), false, 3);
            assertEquals("one two three", editor.edit().selectedText());
        }

        @Test
        @DisplayName("copy, cut and paste go through the clipboard it was given")
        void clipboardRoundTrip() {
            var board = new Board();
            var editor = editor("hello world").clipboard(board);
            var control = new Modifiers(false, true, false, false);

            editor.caretTo(0, false);
            editor.caretTo(5, true);
            assertTrue(editor.onKey(key(Key.C, control)));
            assertEquals("hello", board.text());

            assertTrue(editor.onKey(key(Key.X, control)));
            assertEquals(" world", editor.text());

            editor.caretTo(6, false);
            assertTrue(editor.onKey(key(Key.V, control)));
            assertEquals(" worldhello", editor.text());
        }

        @Test
        @DisplayName("without a clipboard the keys are not consumed")
        void noClipboardIsNotAConsumedKey() {
            var editor = editor("hello");
            editor.caretTo(0, false);
            editor.caretTo(5, true);

            // A feature that is not there must not swallow the key: an application
            // may have its own Ctrl+C.
            assertFalse(editor.copy());
            assertFalse(editor.onKey(key(Key.C, new Modifiers(false, true, false, false))));
        }

        @Test
        @DisplayName("a pasted newline is flattened in a single-line editor")
        void pasteRespectsTheMode() {
            var board = new Board();
            board.text("two\nlines");

            var single = editor("").clipboard(board);
            assertTrue(single.paste());
            assertEquals("two lines", single.text(), "flattened rather than half-dropped");

            var multi = editor("").clipboard(board).multiline(true);
            assertTrue(multi.paste());
            assertEquals("two\nlines", multi.text());
        }

        @Test
        @DisplayName("Ctrl+A selects everything")
        void selectAll() {
            var editor = editor("everything");
            assertTrue(editor.onKey(key(Key.A, new Modifiers(false, true, false, false))));
            assertEquals("everything", editor.edit().selectedText());
        }
    }

    @Nested
    @DisplayName("shaping and geometry")
    class Shaping {

        @Test
        @DisplayName("the paragraph the caret is measured against is the one handed out")
        void oneShapingPerText() {
            var editor = editor("hello");

            var first = editor.paragraph();
            assertSame(first, editor.paragraph(), "asking twice does not reshape");

            editor.onText("!");
            assertEquals("hello!", editor.paragraph().text(), "and typing does");
        }

        @Test
        @DisplayName("changing the wrap width re-lays out the same shaping")
        void wrapWidthRelaysOut() {
            var editor = editor("the quick brown fox jumps over the lazy dog");

            var wide = editor.layout().lineCount();
            editor.wrapWidth(80);
            var narrow = editor.layout().lineCount();

            assertTrue(narrow > wide, "the same text wraps into more lines in a narrower box");
            assertEquals(1, wide, "and was one line when unconstrained");
        }

        @Test
        @DisplayName("an empty editor still has a caret to draw")
        void emptyTextHasACaret() {
            var editor = editor("");

            var caret = editor.caret();
            assertEquals(0, caret.x(), 0.001);
            assertEquals(0, caret.top(), 0.001);
            assertTrue(caret.height() > 0, "a caret in an empty box is a line tall, or there is nothing to see");
            assertTrue(editor.selectionRects().isEmpty());
        }

        /// `text-align`, end to end through one editor: the paint, the caret and
        /// the hit test are the three that have to agree, and an editor is where
        /// they meet (`docs/gaps.md` G30, ADR-0318).
        @Test
        @DisplayName("a centred editor puts its caret where it draws the glyphs")
        void alignmentMovesTheCaretWithTheText() {
            var editor = editor("hello").wrapWidth(200);
            var left = editor.caret().x();

            editor.textAlign(TextAlign.CENTER);

            assertEquals(TextAlign.CENTER, editor.textAlign());
            assertTrue(editor.caret().x() > left, "a centred line starts further in, and so does its caret");
            assertEquals(
                    TextAlign.CENTER.indentOf(editor.layout().lines().getFirst().width(), 200) + left,
                    editor.caret().x(),
                    0.001,
                    "and it is the painter's own indent rather than a second guess at it");
        }

        @Test
        @DisplayName("pressing on a centred caret does not move it")
        void alignmentRoundTripsThroughAPress() {
            var editor = editor("hello there").wrapWidth(200).textAlign(TextAlign.CENTER);
            editor.caretTo(7, false);
            var caret = editor.caret();

            editor.pointerAt(caret.x(), caret.top() + 1, false, 1);

            assertEquals(7, editor.edit().caret(), "the press landed half the line's slack away from the caret");
        }

        @Test
        @DisplayName("the alignment is a late decision and re-wraps nothing")
        void alignmentKeepsTheLayout() {
            var editor = editor("the quick brown fox jumps over the lazy dog").wrapWidth(80);
            var layout = editor.layout();

            editor.textAlign(TextAlign.END);

            assertSame(layout, editor.layout(), "alignment does not decide where lines break");
        }

        @Test
        @DisplayName("Down keeps its column across a run of keys")
        void verticalRunsKeepTheColumn() {
            var editor = editor("aaaaaaaaaa\nbb\ncccccccccc").multiline(true);
            editor.caretTo(8, false);
            var column = editor.caret().x();

            editor.onKey(key(Key.DOWN));
            assertEquals(13, editor.edit().caret(), "the short line has no such column");

            editor.onKey(key(Key.DOWN));
            assertTrue(
                    Math.abs(editor.caret().x() - column) < font.size(),
                    "and the column comes back on the line that is long enough");
        }
    }

    @Test
    @DisplayName("it refuses what it cannot do anything with")
    void refusesNulls() {
        assertThrows(NullPointerException.class, () -> new Editor(null));
        var editor = editor("hi");
        assertThrows(NullPointerException.class, () -> editor.onKey(null));
        assertThrows(NullPointerException.class, () -> editor.onText(null));
        assertThrows(NullPointerException.class, () -> editor.text(null));
    }
}
