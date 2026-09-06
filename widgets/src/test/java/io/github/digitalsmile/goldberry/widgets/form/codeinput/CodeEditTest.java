package io.github.digitalsmile.goldberry.widgets.form.codeinput;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/// §4's editing model for `code-input`, with no widget, no font and no frame —
/// `TextEditTest`'s arrangement and for its reason.
///
/// The specification is four sentences long and every one of them is a test
/// here: typing advances, `Backspace` on an empty box clears the previous one, a
/// paste of the full code fills every box at once, and focus lands wherever the
/// first empty box is.
class CodeEditTest {

    private static CodeEdit six() {
        return CodeEdit.empty(6);
    }

    @Nested
    @DisplayName("typing advances")
    class Typing {

        @Test
        @DisplayName("one character at a time fills the boxes in order")
        void oneAtATime() {
            var edit = six().type("1", CodeType.DIGITS).type("2", CodeType.DIGITS);

            assertEquals("12", edit.value());
            assertEquals("1", edit.boxAt(0));
            assertEquals("2", edit.boxAt(1));
            assertEquals("", edit.boxAt(2));
        }

        @Test
        @DisplayName("a character the type refuses is dropped, and the rest still lands")
        void filtered() {
            var edit = six().type("1a2", CodeType.DIGITS);

            assertEquals("12", edit.value());
        }

        /// The paste out of `Your code is 123 456`, which is §4's "the thing
        /// users actually do" — and the reason [CodeType] drops rather than
        /// refuses. A whole-value filter would have rejected this outright.
        @Test
        @DisplayName("a paste with a space in it fills every box at once")
        void pastedWithSeparators() {
            var edit = six().type("123 456", CodeType.DIGITS);

            assertEquals("123456", edit.value());
            assertTrue(edit.isComplete());
        }

        @Test
        @DisplayName("anything past the last box is dropped")
        void overflow() {
            var edit = six().type("1234567890", CodeType.DIGITS);

            assertEquals("123456", edit.value());
        }

        @Test
        @DisplayName("typing into a full code changes nothing, and says so by identity")
        void full() {
            var edit = six().type("123456", CodeType.DIGITS);

            assertSame(edit, edit.type("7", CodeType.DIGITS));
        }

        @Test
        @DisplayName("nothing acceptable in the text leaves the code alone")
        void nothingAccepted() {
            var edit = six().type("12", CodeType.DIGITS);

            assertSame(edit, edit.type("abc", CodeType.DIGITS));
        }

        @Test
        @DisplayName("alnum takes letters, and digits does not")
        void alphabets() {
            assertEquals("A1", six().type("A1", CodeType.ALNUM).value());
            assertEquals("1", six().type("A1", CodeType.DIGITS).value());
        }

        /// A letter outside the basic plane is one box, not two halves of a
        /// surrogate pair drawn in each of two.
        @Test
        @DisplayName("a code point outside the basic plane takes one box")
        void astral() {
            var deseret = "𐐀";
            var edit = six().type(deseret + "1", CodeType.ALNUM);

            assertEquals(2, edit.filled());
            assertEquals(deseret, edit.boxAt(0));
            assertEquals("1", edit.boxAt(1));
        }
    }

    @Nested
    @DisplayName("backspace")
    class Backspace {

        /// §4: "`Backspace` on an empty box moves back and clears the previous
        /// one" — which is what dropping the last character *is*, given that the
        /// active box is always the first empty one.
        @Test
        @DisplayName("clears the last filled box")
        void clearsTheLast() {
            var edit = six().type("123", CodeType.DIGITS).backspace();

            assertEquals("12", edit.value());
            assertEquals(2, edit.caret());
        }

        @Test
        @DisplayName("on an empty code does nothing, and says so by identity")
        void empty() {
            var edit = six();

            assertSame(edit, edit.backspace());
        }

        @Test
        @DisplayName("on a full code clears the last box rather than the one after it")
        void full() {
            var edit = six().type("123456", CodeType.DIGITS).backspace();

            assertEquals("12345", edit.value());
            assertFalse(edit.isComplete());
        }
    }

    @Nested
    @DisplayName("the active box is the first empty one")
    class Caret {

        @Test
        @DisplayName("an empty code is on the first box")
        void empty() {
            assertEquals(0, six().caret());
        }

        @Test
        @DisplayName("a partly filled one is on the box after what is filled")
        void partial() {
            assertEquals(3, six().type("123", CodeType.DIGITS).caret());
        }

        /// Clamped rather than running off the end: a full code still has to show
        /// a ring somewhere, and the last box is where a `Backspace` would act.
        @Test
        @DisplayName("a full one stays on the last box")
        void full() {
            assertEquals(5, six().type("123456", CodeType.DIGITS).caret());
        }
    }

    @Nested
    @DisplayName("what it is")
    class Shape {

        @Test
        @DisplayName("a value longer than the code is truncated on construction")
        void truncated() {
            assertEquals("123456", new CodeEdit("123456789", 6).value());
        }

        @Test
        @DisplayName("a length of zero is not a code")
        void noBoxes() {
            assertThrows(IllegalArgumentException.class, () -> CodeEdit.empty(0));
        }

        @Test
        @DisplayName("a box outside the code is an error rather than an empty string")
        void outOfRange() {
            assertThrows(IndexOutOfBoundsException.class, () -> six().boxAt(6));
        }

        @Test
        @DisplayName("a value the application sets is filtered like a paste")
        void withValue() {
            assertEquals("12", six().withValue("1a2", CodeType.DIGITS).value());
        }

        @Test
        @DisplayName("resizing keeps what still fits")
        void resized() {
            var edit = six().type("123456", CodeType.DIGITS);

            assertEquals("1234", edit.resized(4).value());
            assertEquals(4, edit.resized(4).length());
        }

        @Test
        @DisplayName("clearing empties every box")
        void cleared() {
            assertTrue(six().type("1234", CodeType.DIGITS).cleared().isEmpty());
        }

        @Test
        @DisplayName("clearing an empty code changes nothing, and says so by identity")
        void clearedEmpty() {
            var edit = six();

            assertSame(edit, edit.cleared());
        }
    }

    @Nested
    @DisplayName("the alphabets")
    class Types {

        @Test
        @DisplayName("named from markup, and an unknown name is null so the caller can log it")
        void named() {
            assertEquals(CodeType.DIGITS, CodeType.named("digits"));
            assertEquals(CodeType.DIGITS, CodeType.named(null));
            assertEquals(CodeType.ALNUM, CodeType.named("ALNUM"));
            assertEquals(CodeType.ALNUM, CodeType.named("alphanumeric"));
            assertEquals(null, CodeType.named("hex"));
        }

        @Test
        @DisplayName("digits is the ten, and nothing that merely looks like one")
        void digits() {
            assertTrue(CodeType.DIGITS.accepts('7'));
            assertFalse(CodeType.DIGITS.accepts('x'));
            // ARABIC-INDIC DIGIT SEVEN: a digit to `Character.isDigit`, and not
            // one of the ten a code is spelled with.
            assertFalse(CodeType.DIGITS.accepts('٧'));
            assertTrue(CodeType.ALNUM.accepts('٧'));
        }
    }
}
