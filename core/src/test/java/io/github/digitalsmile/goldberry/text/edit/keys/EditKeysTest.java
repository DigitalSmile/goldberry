package io.github.digitalsmile.goldberry.text.edit.keys;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import io.github.digitalsmile.goldberry.input.event.KeyEvent;
import io.github.digitalsmile.goldberry.input.key.Key;
import io.github.digitalsmile.goldberry.input.key.Mod;
import io.github.digitalsmile.goldberry.input.key.Modifiers;

/// The one key map, and the three surfaces that read it — [ADR-0376].
///
/// This used to be three tables in three classes that agreed because each was
/// copied from the last. What is asserted here is the table itself, and — for
/// the keys where the three differ — that they differ in exactly the two ways
/// the surfaces are allowed to.
class EditKeysTest {

    private static KeyEvent press(Key key, Modifiers modifiers) {
        return new KeyEvent(KeyEvent.Kind.PRESSED, key, modifiers, false, null);
    }

    private static EditCommand on(EditSurface surface, Key key, Modifiers modifiers) {
        return EditKeys.of(press(key, modifiers), surface);
    }

    private static EditCommand plain(EditSurface surface, Key key) {
        return on(surface, key, Modifiers.NONE);
    }

    @Nested
    @DisplayName("what every surface agrees about")
    class Shared {

        @Test
        @DisplayName("the arrows move by a character, and by a word with Ctrl")
        void arrows() {
            for (var surface : EditSurface.values()) {
                assertEquals(new EditCommand.Move(Motion.LEFT, false, false), plain(surface, Key.LEFT));
                assertEquals(
                        new EditCommand.Move(Motion.RIGHT, true, false),
                        on(surface, Key.RIGHT, Modifiers.of(Mod.CTRL)));
            }
        }

        @Test
        @DisplayName("Shift extends rather than moves")
        void shiftExtends() {
            assertEquals(
                    new EditCommand.Move(Motion.LEFT, false, true),
                    on(EditSurface.DOCUMENT, Key.LEFT, Modifiers.of(Mod.SHIFT)));
        }

        @Test
        @DisplayName("Home and End are the line's; Ctrl makes them the text's")
        void homeAndEnd() {
            for (var surface : EditSurface.values()) {
                assertEquals(new EditCommand.Move(Motion.LINE_START, false, false), plain(surface, Key.HOME));
                assertEquals(
                        new EditCommand.Move(Motion.DOCUMENT_END, true, false),
                        on(surface, Key.END, Modifiers.of(Mod.CTRL)));
            }
        }

        @Test
        @DisplayName("Backspace and Delete take a character, or a word with Ctrl")
        void deletion() {
            assertEquals(new EditCommand.Delete(true, false), plain(EditSurface.FIELD, Key.BACKSPACE));
            assertEquals(
                    new EditCommand.Delete(false, true), on(EditSurface.FIELD, Key.DELETE, Modifiers.of(Mod.CTRL)));
        }

        /// On this desktop the accelerator modifier is `Ctrl`; on macOS the same
        /// six are on `Cmd`, which is what [ADR-0378] is for. The test names the
        /// resolved modifier rather than `Ctrl`, so it says the same thing
        /// wherever it runs.
        @Test
        @DisplayName("the accelerators are the same six on all three")
        void accelerators() {
            var control = Modifiers.of(io.github.digitalsmile.goldberry.input.key.PrimaryModifier.current());
            var controlShift = control.and(Mod.SHIFT);
            for (var surface : EditSurface.values()) {
                assertEquals(EditCommand.Simple.SELECT_ALL, on(surface, Key.A, control));
                assertEquals(EditCommand.Simple.COPY, on(surface, Key.C, control));
                assertEquals(EditCommand.Simple.CUT, on(surface, Key.X, control));
                assertEquals(EditCommand.Simple.PASTE, on(surface, Key.V, control));
                assertEquals(EditCommand.Simple.UNDO, on(surface, Key.Z, control));
                // Both spellings of redo, which every editor here has always
                // taken and which is the first thing a hand-copied table loses.
                assertEquals(EditCommand.Simple.REDO, on(surface, Key.Z, controlShift));
                assertEquals(EditCommand.Simple.REDO, on(surface, Key.Y, control));
            }
        }

        @Test
        @DisplayName("Ctrl+Alt is AltGr and types a character rather than pasting")
        void altGrIsNotAnAccelerator() {
            assertNull(on(EditSurface.DOCUMENT, Key.V, Modifiers.of(Mod.CTRL, Mod.ALT)));
        }

        @Test
        @DisplayName("a key the map has no meaning for is nobody's")
        void unmapped() {
            // Tab moves focus and Escape closes what it closes: an editor that
            // consumed either would trap the user in it.
            assertNull(plain(EditSurface.DOCUMENT, Key.TAB));
            assertNull(plain(EditSurface.DOCUMENT, Key.ESCAPE));
        }

        @Test
        @DisplayName("and a release asks for nothing at all")
        void releasesAreNotPresses() {
            assertNull(EditKeys.of(
                    new KeyEvent(KeyEvent.Kind.RELEASED, Key.LEFT, Modifiers.NONE, false, null), EditSurface.FIELD));
        }
    }

    @Nested
    @DisplayName("the two things a surface changes")
    class PerSurface {

        @Test
        @DisplayName("Up is a line where there are lines, and the start of the text where there are not")
        void vertical() {
            assertEquals(new EditCommand.MoveLine(-1, false, false), plain(EditSurface.DOCUMENT, Key.UP));
            assertEquals(new EditCommand.MoveLine(1, false, false), plain(EditSurface.WRAPPED, Key.DOWN));
            // A field must still take them, or Up would walk out of a vertical
            // focus scope from a field somebody is editing.
            assertEquals(new EditCommand.Move(Motion.DOCUMENT_START, false, false), plain(EditSurface.FIELD, Key.UP));
            assertEquals(new EditCommand.Move(Motion.DOCUMENT_END, false, false), plain(EditSurface.FIELD, Key.DOWN));
        }

        @Test
        @DisplayName("the page keys are lines too, and a field has no page")
        void pages() {
            assertEquals(new EditCommand.MoveLine(-1, true, false), plain(EditSurface.DOCUMENT, Key.PAGE_UP));
            assertNull(plain(EditSurface.FIELD, Key.PAGE_DOWN));
        }

        @Test
        @DisplayName("Enter is a newline only where newlines are typed")
        void newlines() {
            assertEquals(new EditCommand.Type("\n"), plain(EditSurface.DOCUMENT, Key.ENTER));
            // A form's default button needs it, and so does whatever is listening
            // on a canvas editor that was not made multiline.
            assertNull(plain(EditSurface.FIELD, Key.ENTER));
            assertNull(plain(EditSurface.WRAPPED, Key.ENTER));
        }
    }

    @Nested
    @DisplayName("which commands a read-only editor must refuse")
    class ReadOnly {

        @Test
        @DisplayName("the four that change the text")
        void edits() {
            assertTrue(EditCommand.Simple.CUT.isEdit());
            assertTrue(EditCommand.Simple.PASTE.isEdit());
            assertTrue(EditCommand.Simple.UNDO.isEdit());
            assertTrue(EditCommand.Simple.REDO.isEdit());
        }

        @Test
        @DisplayName("and not the two that do not")
        void reads() {
            // Copying out of a read-only field is the *point* of a read-only
            // field, and selecting all of it is how you copy it.
            org.junit.jupiter.api.Assertions.assertFalse(EditCommand.Simple.COPY.isEdit());
            org.junit.jupiter.api.Assertions.assertFalse(EditCommand.Simple.SELECT_ALL.isEdit());
        }
    }
}
