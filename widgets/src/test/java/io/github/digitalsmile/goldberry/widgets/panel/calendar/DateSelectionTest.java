package io.github.digitalsmile.goldberry.widgets.panel.calendar;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.LocalDate;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/// §10's three selection models, with no widget — `TextEdit`'s arrangement and
/// for its reason.
///
/// Every rule about what pressing a day does is [DateSelection#with], and every
/// one of them is here.
class DateSelectionTest {

    private static LocalDate day(int of) {
        return LocalDate.of(2026, 9, of);
    }

    @Nested
    @DisplayName("a single date")
    class Single {

        @Test
        @DisplayName("a press replaces whatever was chosen")
        void replaces() {
            var selection = DateSelection.of(day(3)).with(day(9));

            assertEquals(List.of(day(9)), selection.dates());
        }

        /// A single-date calendar with nothing in it is a state a user reaches by
        /// accident and cannot see, and a `date-picker` would empty its own field
        /// on a second click.
        @Test
        @DisplayName("pressing the chosen day again keeps it")
        void keepsOnRepeat() {
            var selection = DateSelection.of(day(3)).with(day(3));

            assertEquals(List.of(day(3)), selection.dates());
        }

        @Test
        @DisplayName("holds one date and refuses two")
        void refusesTwo() {
            assertThrows(
                    IllegalArgumentException.class,
                    () -> new DateSelection(DateSelection.Mode.SINGLE, List.of(day(1), day(2))));
        }
    }

    @Nested
    @DisplayName("several dates")
    class Multiple {

        private DateSelection empty() {
            return DateSelection.empty(DateSelection.Mode.MULTIPLE);
        }

        @Test
        @DisplayName("every press is a toggle, with no modifier to learn")
        void toggles() {
            var chosen = empty().with(day(3)).with(day(9)).with(day(3));

            assertEquals(List.of(day(9)), chosen.dates());
        }

        /// What an application reporting a set back to a server wants; sorting it
        /// would throw the information away.
        @Test
        @DisplayName("the order is the order they were pressed in")
        void keepsOrder() {
            var chosen = empty().with(day(9)).with(day(3)).with(day(20));

            assertEquals(List.of(day(9), day(3), day(20)), chosen.dates());
        }
    }

    @Nested
    @DisplayName("a range")
    class Range {

        private DateSelection empty() {
            return DateSelection.empty(DateSelection.Mode.RANGE);
        }

        @Test
        @DisplayName("the first press starts it and the second completes it")
        void twoPresses() {
            var half = empty().with(day(3));
            assertEquals(day(3), half.first());
            assertNull(half.last());

            var whole = half.with(day(9));
            assertEquals(day(3), whole.first());
            assertEquals(day(9), whole.last());
        }

        /// A range dragged backwards is the same range, and an application should
        /// not have to normalise it.
        @Test
        @DisplayName("completing it before the start orders the pair")
        void backwards() {
            var whole = empty().with(day(9)).with(day(3));

            assertEquals(day(3), whole.first());
            assertEquals(day(9), whole.last());
        }

        @Test
        @DisplayName("a third press starts again")
        void restarts() {
            var next = empty().with(day(3)).with(day(9)).with(day(20));

            assertEquals(List.of(day(20)), next.dates());
        }

        /// §2: "radius `full` on the selected day, range ends only". The ends are
        /// `contains` and the middle is `covers`, which is what lets the
        /// stylesheet round one and leave the other square with no exception.
        @Test
        @DisplayName("the ends are chosen dates and the middle is only covered")
        void endsAndMiddle() {
            var whole = empty().with(day(3)).with(day(9));

            assertTrue(whole.contains(day(3)));
            assertTrue(whole.isStart(day(3)));
            assertFalse(whole.covers(day(3)));

            assertTrue(whole.contains(day(9)));
            assertTrue(whole.isEnd(day(9)));
            assertFalse(whole.covers(day(9)));

            assertFalse(whole.contains(day(5)));
            assertTrue(whole.covers(day(5)));

            assertFalse(whole.covers(day(20)));
        }

        @Test
        @DisplayName("a half-chosen range covers nothing")
        void halfCoversNothing() {
            var half = empty().with(day(3));

            assertFalse(half.covers(day(5)));
        }
    }

    @Nested
    @DisplayName("changing model")
    class Reading {

        /// Anything that does not fit is dropped rather than reshaped: five dates
        /// read as a range would have to invent which two were meant.
        @Test
        @DisplayName("several dates read as one keeps the first")
        void multipleToSingle() {
            var several = DateSelection.empty(DateSelection.Mode.MULTIPLE)
                    .with(day(3))
                    .with(day(9))
                    .with(day(20));

            assertEquals(List.of(day(3)), several.as(DateSelection.Mode.SINGLE).dates());
        }

        @Test
        @DisplayName("several dates read as a range keeps the first two, ordered")
        void multipleToRange() {
            var several = DateSelection.empty(DateSelection.Mode.MULTIPLE)
                    .with(day(9))
                    .with(day(3))
                    .with(day(20));

            var range = several.as(DateSelection.Mode.RANGE);
            assertEquals(day(3), range.first());
            assertEquals(day(9), range.last());
        }

        @Test
        @DisplayName("reading it as what it already is hands the same instance back")
        void unchanged() {
            var selection = DateSelection.of(day(3));

            assertSame(selection, selection.as(DateSelection.Mode.SINGLE));
        }
    }
}
