package io.github.digitalsmile.goldberry.widgets.form.textinput;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.digitalsmile.goldberry.widgets.form.textinput.EditHistory.Kind;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/// Undo, redo, and what folds into one step.
///
/// The coalescing rule is one comparison — does this change start where the last
/// one ended — and most of these tests exist to show that the behaviours nobody
/// wrote a rule for fall out of it.
class EditHistoryTest {

    /// Types `text` one character at a time, recording each keystroke, and
    /// returns where it ended up. What a user does, at the granularity the widget
    /// will call [EditHistory#record] at.
    private static TextEdit type(EditHistory history, TextEdit from, String text) {
        var edit = from;
        for (var i = 0; i < text.length(); i++) {
            var before = edit;
            edit = edit.insert(text.substring(i, i + 1));
            history.record(before, edit, Kind.TYPING);
        }
        return edit;
    }

    @Nested
    @DisplayName("one step back")
    class Undo {

        @Test
        @DisplayName("a fresh history has nothing to undo")
        void startsEmpty() {
            var history = new EditHistory();
            var edit = TextEdit.of("Goldberry");

            assertFalse(history.canUndo());
            assertFalse(history.canRedo());
            assertSame(edit, history.undo(edit), "undoing nothing changed the field");
        }

        @Test
        @DisplayName("a typed run comes back as one word, not one letter")
        void coalescesTyping() {
            var history = new EditHistory();

            var typed = type(history, TextEdit.EMPTY, "Goldberry");

            assertEquals("Goldberry", typed.text());
            assertEquals(1, history.depth(), "nine keystrokes should be one undo step");
            assertEquals("", history.undo(typed).text());
        }

        @Test
        @DisplayName("a paste is its own step however long it is")
        void pasteIsOneStep() {
            var history = new EditHistory();

            var before = TextEdit.EMPTY;
            var after = before.insert("pasted from somewhere else");
            history.record(before, after, Kind.OTHER);

            assertEquals(1, history.depth());
            assertEquals("", history.undo(after).text());
        }

        @Test
        @DisplayName("two pastes never fold together")
        void pastesDoNotFold() {
            var history = new EditHistory();

            var one = TextEdit.EMPTY.insert("first");
            history.record(TextEdit.EMPTY, one, Kind.OTHER);
            var two = one.insert(" second");
            history.record(one, two, Kind.OTHER);

            // Each is one thing the user did deliberately, so each is one Ctrl+Z.
            assertEquals(2, history.depth());
            assertEquals("first", history.undo(two).text());
        }

        @Test
        @DisplayName("a change that changed nothing is not a step")
        void ignoresNoOps() {
            var history = new EditHistory();
            var edit = TextEdit.of("abc").caretTo(0, false);

            // Backspace at the start of a field. Recording it would make the next
            // Ctrl+Z do nothing, which reads as broken.
            history.record(edit, edit.backspace(), Kind.DELETING);

            assertFalse(history.canUndo());
        }
    }

    @Nested
    @DisplayName("what breaks a run")
    class Breaks {

        @Test
        @DisplayName("moving the caret, without anything being written down about carets")
        void caretMoveBreaksTheRun() {
            var history = new EditHistory();

            var typed = type(history, TextEdit.EMPTY, "Gold");
            var moved = typed.caretTo(0, false);
            var more = type(history, moved, "X");

            // The rule is "this change starts where the last one ended"; a caret
            // move means it does not. Nothing here knows what a caret is.
            assertEquals(2, history.depth());
            assertEquals("Gold", history.undo(more).text());
        }

        @Test
        @DisplayName("deleting after typing")
        void kindChangeBreaksTheRun() {
            var history = new EditHistory();

            var typed = type(history, TextEdit.EMPTY, "Gold");
            var deleted = typed.backspace();
            history.record(typed, deleted, Kind.DELETING);

            assertEquals(2, history.depth());
            assertEquals("Gold", history.undo(deleted).text());
        }

        @Test
        @DisplayName("typing after deleting")
        void typingAfterDeleting() {
            var history = new EditHistory();

            var start = TextEdit.of("Goldberry");
            var deleted = start.backspace();
            history.record(start, deleted, Kind.DELETING);
            var typed = type(history, deleted, "s");

            assertEquals(2, history.depth());
            assertEquals("Goldberr", history.undo(typed).text());
        }

        @Test
        @DisplayName("a run explicitly ended, for the boundaries the rule cannot see")
        void endRunBreaksIt() {
            var history = new EditHistory();

            var typed = type(history, TextEdit.EMPTY, "Gold");
            history.endRun();
            var more = type(history, typed, "berry");

            // Losing focus is a boundary a user believes in although nothing
            // about the text changed.
            assertEquals(2, history.depth());
            assertEquals("Gold", history.undo(more).text());
        }

        @Test
        @DisplayName("a run of deletes folds like a run of keystrokes")
        void deletesFold() {
            var history = new EditHistory();

            var edit = TextEdit.of("Goldberry");
            for (var i = 0; i < 5; i++) {
                var before = edit;
                edit = edit.backspace();
                history.record(before, edit, Kind.DELETING);
            }

            assertEquals("Gold", edit.text());
            assertEquals(1, history.depth());
            assertEquals("Goldberry", history.undo(edit).text());
        }
    }

    @Nested
    @DisplayName("forward again")
    class Redo {

        @Test
        @DisplayName("takes back what undo gave up")
        void roundTrips() {
            var history = new EditHistory();
            var typed = type(history, TextEdit.EMPTY, "Goldberry");

            var undone = history.undo(typed);
            assertEquals("", undone.text());
            assertTrue(history.canRedo());

            var redone = history.redo(undone);
            assertEquals("Goldberry", redone.text());
            assertFalse(history.canRedo());
            assertTrue(history.canUndo(), "redoing put the step back on the undo stack");
        }

        @Test
        @DisplayName("walks a whole stack in both directions")
        void walksTheStack() {
            var history = new EditHistory();

            var one = type(history, TextEdit.EMPTY, "Yoga");
            history.endRun();
            var two = type(history, one, " laid");
            history.endRun();
            var three = type(history, two, " this out");

            var back = history.undo(three);
            assertEquals("Yoga laid", back.text());
            back = history.undo(back);
            assertEquals("Yoga", back.text());
            back = history.undo(back);
            assertEquals("", back.text());
            assertFalse(history.canUndo());

            var forward = history.redo(back);
            assertEquals("Yoga", forward.text());
            forward = history.redo(forward);
            assertEquals("Yoga laid", forward.text());
            forward = history.redo(forward);
            assertEquals("Yoga laid this out", forward.text());
        }

        @Test
        @DisplayName("typing something new abandons the future")
        void newEditClearsRedo() {
            var history = new EditHistory();
            var typed = type(history, TextEdit.EMPTY, "Goldberry");

            var undone = history.undo(typed);
            var different = type(history, undone, "Tom");

            assertFalse(history.canRedo(),
                    "the future you undid your way out of is not reachable once you type");
            assertEquals("Tom", different.text());
        }

        @Test
        @DisplayName("a redone state is not folded into by the next keystroke")
        void redoEndsTheRun() {
            var history = new EditHistory();
            var typed = type(history, TextEdit.EMPTY, "Gold");

            var undone = history.undo(typed);
            var redone = history.redo(undone);
            var more = type(history, redone, "berry");

            assertEquals("Gold", history.undo(more).text(),
                    "the keystrokes after a redo folded into the step the redo restored");
        }
    }

    @Nested
    @DisplayName("bounds")
    class Bounds {

        @Test
        @DisplayName("keeps at most DEPTH steps, dropping the oldest")
        void isBounded() {
            var history = new EditHistory();

            var edit = TextEdit.EMPTY;
            for (var i = 0; i < EditHistory.DEPTH + 50; i++) {
                var before = edit;
                edit = edit.insert("x");
                // Each keystroke its own step, which is what a paste-heavy
                // session looks like -- and the case the bound exists for.
                history.record(before, edit, Kind.OTHER);
            }

            assertEquals(EditHistory.DEPTH, history.depth());

            // The oldest went, so undoing all the way back does not reach empty.
            var back = edit;
            while (history.canUndo()) {
                back = history.undo(back);
            }
            assertEquals(50, back.length());
        }

        @Test
        @DisplayName("clearing forgets both directions")
        void clears() {
            var history = new EditHistory();
            var typed = type(history, TextEdit.EMPTY, "Goldberry");
            history.undo(typed);

            history.clear();

            assertFalse(history.canUndo());
            assertFalse(history.canRedo());
        }
    }
}
