package io.github.digitalsmile.goldberry.widgets.form.parts;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import io.github.digitalsmile.goldberry.text.edit.TextEdit;

/// The arithmetic behind `max-length`, which `text-input` and `text-area` used to
/// carry a copy each of.
///
/// The bug that brought it here: [MaxLength#clip] counted **code points** from
/// the front and then took `Math.min` of the offset it found and the room it had,
/// which undoes the step it had just taken. A limit landing inside a surrogate
/// pair therefore kept the lead and dropped the trail — `clip("a🎨b", 2)` ended
/// in a lone high surrogate, which is not a character, and the comment above it
/// claimed the opposite.
class MaxLengthTest {

    @Nested
    @DisplayName("clipping an insertion to what fits")
    class Clipping {

        @Test
        @DisplayName("cuts a plain string at the limit")
        void cutsAtTheLimit() {
            assertEquals("Gold", MaxLength.clip("Goldberry", 4));
        }

        @Test
        @DisplayName("leaves a string that already fits alone")
        void keepsWhatFits() {
            assertEquals("Gold", MaxLength.clip("Gold", 4));
            assertEquals("Gold", MaxLength.clip("Gold", 9));
        }

        @Test
        @DisplayName("no room at all takes nothing")
        void noRoom() {
            assertEquals("", MaxLength.clip("Goldberry", 0));
            assertEquals("", MaxLength.clip("Goldberry", -1));
        }

        @Test
        @DisplayName("a limit inside a surrogate pair drops the whole character")
        void doesNotSplitASurrogatePair() {
            // Two chars of room, and the second is the lead of the palette's
            // pair. Keeping it is what a `char`-counting cut does and it is not a
            // string any renderer or application can use.
            assertEquals("a", MaxLength.clip("a🎨b", 2));
            assertEquals("a🎨", MaxLength.clip("a🎨b", 3));
            assertEquals("", MaxLength.clip("🎨b", 1));
        }

        @Test
        @DisplayName("and a limit inside a cluster drops that, which is what the comment always claimed")
        void doesNotSplitACluster() {
            // An accented `e` written as `e` plus a combining acute: two chars,
            // one character. A cut between them leaves an accent with nothing to
            // sit on. Spelled out rather than typed, so that a normalising editor
            // cannot quietly turn this test into the one above it.
            var accented = "e\u0301f";

            assertEquals("", MaxLength.clip(accented, 1));
            assertEquals("e\u0301", MaxLength.clip(accented, 2));
        }
    }

    @Nested
    @DisplayName("how much room is left")
    class Room {

        @Test
        @DisplayName("a negative maximum is no limit at all")
        void unlimited() {
            assertEquals(-1, MaxLength.room(-1, TextEdit.of("Goldberry")));
        }

        @Test
        @DisplayName("what is left is the maximum less what is held")
        void whatIsLeft() {
            assertEquals(2, MaxLength.room(6, TextEdit.of("Gold")));
            assertEquals(0, MaxLength.room(4, TextEdit.of("Gold")));
        }

        @Test
        @DisplayName("a selection is room, because typing over a full field's selection must work")
        void aSelectionIsRoom() {
            assertEquals(4, MaxLength.room(4, new TextEdit("Gold", 0, 4)));
        }

        @Test
        @DisplayName("never negative, whatever a value longer than the limit was set by hand")
        void neverNegative() {
            assertEquals(0, MaxLength.room(2, TextEdit.of("Goldberry")));
        }
    }
}
