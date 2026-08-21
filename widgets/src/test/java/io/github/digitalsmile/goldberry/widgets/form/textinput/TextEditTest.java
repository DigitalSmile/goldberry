package io.github.digitalsmile.goldberry.widgets.form.textinput;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/// `docs/core-widgets.md` §4's editing model, with no widget, no font and no
/// window.
///
/// Every rule a text field has is in here, which is the point of the model being
/// a value: the widget above it decides what a key *means* and this decides what
/// the edit *is*, so the hard half is testable without a frame.
class TextEditTest {

    @Nested
    @DisplayName("what is selected")
    class Selection {

        @Test
        @DisplayName("a fresh value has its caret at the end and nothing selected")
        void startsAtTheEnd() {
            var edit = TextEdit.of("Goldberry");

            assertEquals(9, edit.caret());
            assertFalse(edit.hasSelection());
            assertEquals("", edit.selectedText());
        }

        @Test
        @DisplayName("keeps the direction it was made in")
        void keepsDirection() {
            // Dragged right-to-left: the anchor is at 5 and the caret at 2, and
            // the pair being ordered would lose which end the next Shift+Left
            // moves.
            var edit = new TextEdit("Goldberry", 5, 2);

            assertEquals(2, edit.start());
            assertEquals(5, edit.end());
            assertEquals("ldb", edit.selectedText());

            var extended = edit.left(true);
            assertEquals(1, extended.caret());
            assertEquals(5, extended.anchor(), "extending moved the anchor");
        }

        @Test
        @DisplayName("select-all leaves the caret at the end")
        void selectsAll() {
            var edit = TextEdit.of("Goldberry").caretTo(3, false).selectAll();

            assertEquals(0, edit.anchor());
            assertEquals(9, edit.caret());
            assertEquals("Goldberry", edit.selectedText());
        }

        @Test
        @DisplayName("offsets outside the text are clamped rather than thrown")
        void clampsOffsets() {
            // These arrive from a click, and a click has no opinion about how
            // long the text is.
            assertEquals(9, TextEdit.of("Goldberry").caretTo(500, false).caret());
            assertEquals(0, TextEdit.of("Goldberry").caretTo(-4, false).caret());
        }
    }

    @Nested
    @DisplayName("moving")
    class Moving {

        @Test
        @DisplayName("an arrow collapses a selection to the end it points at")
        void arrowCollapses() {
            var selected = new TextEdit("Goldberry", 2, 5);

            // Not "move one from the caret" -- Left on a selection puts the caret
            // at its start and moves nothing, which is what every editor does.
            assertEquals(2, selected.left(false).caret());
            assertEquals(5, selected.right(false).caret());
            assertFalse(selected.left(false).hasSelection());
        }

        @Test
        @DisplayName("stops at the ends rather than wrapping")
        void stopsAtTheEnds() {
            assertEquals(0, TextEdit.of("abc").caretTo(0, false).left(false).caret());
            assertEquals(3, TextEdit.of("abc").right(false).caret());
        }

        @Test
        @DisplayName("a word step lands on word starts, not on the edges of spaces")
        void walksWords() {
            var edit = TextEdit.of("Yoga laid this out");

            // BreakIterator reports a boundary at both ends of the run of spaces
            // too. Stepping onto one of those would move the caret somewhere no
            // user would call a word.
            assertEquals(15, edit.wordLeft(false).caret());
            assertEquals(10, edit.wordLeft(false).wordLeft(false).caret());
            assertEquals(5, edit.wordLeft(false).wordLeft(false).wordLeft(false).caret());
            assertEquals(0, edit.caretTo(2, false).wordLeft(false).caret());
        }

        @Test
        @DisplayName("a word step forward takes the space with it")
        void wordRightTakesTheSpace() {
            var edit = TextEdit.of("Yoga laid this out").caretTo(0, false);

            assertEquals(5, edit.wordRight(false).caret());
            assertEquals(10, edit.wordRight(false).wordRight(false).caret());
            assertEquals(18, edit.caretTo(15, false).wordRight(false).caret());
        }

        @Test
        @DisplayName("a double-click selects the word under it")
        void selectsAWord() {
            var edit = TextEdit.of("Yoga laid this out");

            assertEquals("laid", edit.wordAt(6).selectedText());
            assertEquals("laid", edit.wordAt(5).selectedText());
            assertEquals("Yoga", edit.wordAt(0).selectedText());
            assertEquals("out", edit.wordAt(16).selectedText());
        }

        @Test
        @DisplayName("a double-click in a gap selects the gap")
        void selectsWhitespace() {
            // The alternative -- snap to the nearer word -- makes the result
            // depend on which half of a space the click landed in.
            assertEquals(" ", TextEdit.of("Yoga laid").wordAt(4).selectedText());
        }

        @Test
        @DisplayName("a double-click on empty text does nothing")
        void wordAtOnEmpty() {
            var empty = TextEdit.EMPTY;

            assertSame(empty, empty.wordAt(0));
        }
    }

    @Nested
    @DisplayName("editing")
    class Editing {

        @Test
        @DisplayName("typing replaces what is selected")
        void typingReplaces() {
            var edit = new TextEdit("Goldberry", 4, 9).insert("en");

            assertEquals("Golden", edit.text());
            assertEquals(6, edit.caret());
            assertFalse(edit.hasSelection());
        }

        @Test
        @DisplayName("typing with no selection inserts at the caret")
        void typingInserts() {
            var edit = TextEdit.of("Godberry").caretTo(2, false).insert("l");

            assertEquals("Goldberry", edit.text());
            assertEquals(3, edit.caret());
        }

        @Test
        @DisplayName("backspace with a selection deletes the selection and no more")
        void backspaceOnSelection() {
            var edit = new TextEdit("Goldberry", 4, 9).backspace();

            assertEquals("Gold", edit.text());
            assertEquals(4, edit.caret());
        }

        @Test
        @DisplayName("backspace and delete step over the ends without moving")
        void deletesAtTheEnds() {
            var start = TextEdit.of("abc").caretTo(0, false);
            assertSame(start, start.backspace());

            var end = TextEdit.of("abc");
            assertSame(end, end.delete());
        }

        @Test
        @DisplayName("a word delete closes the gap it leaves")
        void deletesWords() {
            var edit = TextEdit.of("Yoga laid this out").deleteWordBefore();
            assertEquals("Yoga laid this ", edit.text());
            assertEquals(15, edit.caret());

            var forward = TextEdit.of("Yoga laid this out").caretTo(5, false).deleteWordAfter();
            assertEquals("Yoga this out", forward.text());
            assertEquals(5, forward.caret());
        }

        @Test
        @DisplayName("a word delete with a selection deletes the selection instead")
        void wordDeleteRespectsSelection() {
            var edit = new TextEdit("Yoga laid this out", 5, 9).deleteWordBefore();

            assertEquals("Yoga  this out", edit.text());
        }

        @Test
        @DisplayName("inserting nothing with nothing selected changes nothing")
        void emptyInsertIsIdentity() {
            var edit = TextEdit.of("Goldberry");

            assertSame(edit, edit.insert(""));
        }

        @Test
        @DisplayName("inserting nothing with a selection deletes it")
        void emptyInsertDeletes() {
            // The path Cut takes, and the reason the empty insert is not simply
            // refused: "replace the selection with nothing" is a real edit.
            assertEquals("Gold", new TextEdit("Goldberry", 4, 9).insert("").text());
        }
    }

    @Nested
    @DisplayName("graphemes")
    class Graphemes {

        @Test
        @DisplayName("backspace deletes a whole combining sequence")
        void backspaceDeletesTheCluster() {
            // "e" plus a combining acute -- two chars, one thing on screen.
            // Deleting one char would leave a bare accent.
            var edit = TextEdit.of("café").backspace();

            assertEquals("caf", edit.text());
        }

        @Test
        @DisplayName("backspace deletes a whole surrogate pair")
        void backspaceDeletesThePair() {
            assertEquals("a", TextEdit.of("a🎨").backspace().text());
        }

        @Test
        @DisplayName("an arrow steps over a pair rather than into it")
        void arrowsStepClusters() {
            var edit = TextEdit.of("a🎨b").caretTo(0, false);

            assertEquals(1, edit.right(false).caret());
            assertEquals(3, edit.right(false).right(false).caret(), "the caret landed inside the pair");
            assertEquals(4, edit.right(false).right(false).right(false).caret());
        }

        @Test
        @DisplayName("an offset inside a cluster snaps back out of it")
        void snapsOutOfClusters() {
            // Offset 2 is the middle of the surrogate pair. It is a real index
            // and not a caret position, and it arrives here from a click.
            assertEquals(1, TextEdit.of("a🎨b").caretTo(2, false).caret());
        }
    }

    @Nested
    @DisplayName("a value arriving from outside")
    class Rebinding {

        @Test
        @DisplayName("keeps the caret where it was")
        void keepsTheCaret() {
            // A filter or a formatter rewriting what was typed must not move the
            // caret out from under somebody mid-word.
            var edit = TextEdit.of("1234").caretTo(2, false).withText("12 34");

            assertEquals("12 34", edit.text());
            assertEquals(2, edit.caret());
        }

        @Test
        @DisplayName("clamps a caret the new text is too short for")
        void clampsToShorterText() {
            var edit = TextEdit.of("Goldberry").withText("Gold");

            assertEquals(4, edit.caret());
        }

        @Test
        @DisplayName("the same text is the same value")
        void sameTextIsIdentity() {
            var edit = TextEdit.of("Goldberry").caretTo(3, false);

            assertSame(edit, edit.withText("Goldberry"));
        }

        @Test
        @DisplayName("does not leave the caret inside a cluster")
        void snapsAfterRebinding() {
            var edit = new TextEdit("abcd", 2, 2).withText("a🎨");

            assertTrue(edit.caret() == 1 || edit.caret() == 3,
                    "the caret landed inside the pair at " + edit.caret());
        }
    }
}
