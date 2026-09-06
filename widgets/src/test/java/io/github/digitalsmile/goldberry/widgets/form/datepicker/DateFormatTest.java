package io.github.digitalsmile.goldberry.widgets.form.datepicker;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import io.github.digitalsmile.goldberry.widgets.panel.calendar.DateSelection;

/// What a `date-picker`'s field says and what it makes of what was typed —
/// with no widget, no font and no frame.
///
/// **ISO throughout**, and deliberately: §4 says the toolkit does not invent a
/// date syntax, so a test that asserted `01/09/2026` would be asserting a
/// `java.time` locale table rather than anything this class decides. What this
/// class does decide is the *range* separator and what happens to text that does
/// not parse, and those are what is here.
class DateFormatTest {

    private static final DateFormat ISO = DateFormat.of(DateTimeFormatter.ISO_LOCAL_DATE);

    private static LocalDate day(int of) {
        return LocalDate.of(2026, 9, of);
    }

    @Nested
    @DisplayName("one date")
    class Single {

        @Test
        @DisplayName("is written and read back with the same formatter")
        void roundTrip() {
            var written = ISO.format(day(14));

            assertEquals("2026-09-14", written);
            assertEquals(day(14), ISO.parse(written, DateSelection.Mode.SINGLE).first());
        }

        /// The three answers are genuinely different and the field acts
        /// differently on each: blank clears the value, rubbish leaves the last
        /// one alone.
        @Test
        @DisplayName("blank is an empty selection and rubbish is null")
        void blankAndRubbish() {
            var blank = ISO.parse("   ", DateSelection.Mode.SINGLE);
            assertNotNull(blank);
            assertTrue(blank.isEmpty());

            assertNull(ISO.parse("not a date", DateSelection.Mode.SINGLE));
        }

        @Test
        @DisplayName("a half-typed date is not a date yet")
        void halfTyped() {
            assertNull(ISO.parse("2026-09-", DateSelection.Mode.SINGLE));
        }

        @Test
        @DisplayName("nothing chosen is written as nothing")
        void empty() {
            assertEquals("", ISO.format(DateSelection.NONE));
        }
    }

    @Nested
    @DisplayName("a range")
    class Range {

        @Test
        @DisplayName("is one string with an en dash in it")
        void written() {
            var range = DateSelection.range(day(8), day(21));

            assertEquals("2026-09-08 – 2026-09-21", ISO.format(range));
        }

        /// Parsing is looser than formatting on purpose: a plain hyphen is what a
        /// keyboard has.
        @Test
        @DisplayName("and reads back a hyphen as well as a dash")
        void read() {
            for (var typed :
                    new String[] {"2026-09-08 – 2026-09-21", "2026-09-08 - 2026-09-21", "2026-09-08 to 2026-09-21"}) {
                var range = ISO.parse(typed, DateSelection.Mode.RANGE);
                assertNotNull(range, typed + " did not parse");
                assertEquals(day(8), range.first(), typed);
                assertEquals(day(21), range.last(), typed);
            }
        }

        /// The state between the two presses in the grid. Refusing it would make
        /// the first half of a typed range vanish.
        @Test
        @DisplayName("one date in a range field is a range half chosen")
        void halfChosen() {
            var half = ISO.parse("2026-09-08", DateSelection.Mode.RANGE);

            assertNotNull(half);
            assertEquals(day(8), half.first());
            assertNull(half.last());
        }

        /// The separator cannot be a bare hyphen, because a hyphen is inside an
        /// ISO date — this is the case that says so.
        @Test
        @DisplayName("a single ISO date is not mistaken for a range")
        void notSplitOnItsOwnHyphens() {
            var one = ISO.parse("2026-09-08", DateSelection.Mode.RANGE);

            assertNotNull(one);
            assertEquals(1, one.dates().size());
        }

        @Test
        @DisplayName("a range typed backwards is the same range")
        void backwards() {
            var range = ISO.parse("2026-09-21 – 2026-09-08", DateSelection.Mode.RANGE);

            assertNotNull(range);
            assertEquals(day(8), range.first());
            assertEquals(day(21), range.last());
        }
    }

    @Nested
    @DisplayName("the default")
    class LocaleDefault {

        /// §4: "defaulting to the locale's short form". Asserted against
        /// `java.time`'s own answer rather than against a literal, for the reason
        /// in the class note.
        @Test
        @DisplayName("is the locale's short form, whatever that is here")
        void shortForm() {
            var uk = DateFormat.of(Locale.UK);
            var expected = DateTimeFormatter.ofLocalizedDate(java.time.format.FormatStyle.SHORT)
                    .withLocale(Locale.UK)
                    .format(day(14));

            assertEquals(expected, uk.format(day(14)));
            assertEquals(day(14), uk.parse(expected, DateSelection.Mode.SINGLE).first());
        }
    }
}
