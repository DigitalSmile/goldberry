package io.github.digitalsmile.goldberry.widgets.form.timepicker;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/// What a `time-picker`'s field says and what it makes of what was typed —
/// with no widget, no font and no frame.
class TimeFormatTest {

    private static final TimeFormat HH_MM = TimeFormat.of(DateTimeFormatter.ofPattern("HH:mm", Locale.ROOT));

    @Test
    @DisplayName("a time is written and read back with the same formatter")
    void roundTrip() {
        assertEquals("09:30", HH_MM.format(LocalTime.of(9, 30)));
        assertEquals(LocalTime.of(9, 30), HH_MM.parse("09:30"));
    }

    /// Blank and rubbish both answer null here, and the caller tells them apart
    /// with [TimeFormat#isBlank] — because the field acts differently on each.
    @Test
    @DisplayName("blank and rubbish are both null, and isBlank tells them apart")
    void blankAndRubbish() {
        assertNull(HH_MM.parse("  "));
        assertNull(HH_MM.parse("half nine"));

        assertTrue(TimeFormat.isBlank("  "));
        assertFalse(TimeFormat.isBlank("half nine"));
    }

    @Test
    @DisplayName("a half-typed time is not a time yet")
    void halfTyped() {
        assertNull(HH_MM.parse("09:"));
    }

    @Test
    @DisplayName("nothing chosen is written as nothing")
    void nothing() {
        assertEquals("", HH_MM.format(null));
    }

    /// §4's "defaulting to the locale's short form" — asserted against
    /// `java.time`'s own answer rather than a literal, because a literal would be
    /// asserting a locale table.
    @Test
    @DisplayName("the default at minute precision is the locale's short form")
    void localeShortForm() {
        var uk = TimeFormat.of(Locale.UK, TimePrecision.MINUTES);
        var expected = DateTimeFormatter.ofLocalizedTime(java.time.format.FormatStyle.SHORT)
                .withLocale(Locale.UK)
                .format(LocalTime.of(9, 30));

        assertEquals(expected, uk.format(LocalTime.of(9, 30)));
    }

    /// The one place this class writes a pattern, and only because no
    /// `FormatStyle` produces a time with seconds and without a zone — a picker
    /// with a seconds column needs a field that can show one.
    @Test
    @DisplayName("and at second precision it shows seconds, which no FormatStyle does")
    void withSeconds() {
        var format = TimeFormat.of(Locale.UK, TimePrecision.SECONDS);

        assertEquals("09:30:45", format.format(LocalTime.of(9, 30, 45)));
        assertEquals(LocalTime.of(9, 30, 45), format.parse("09:30:45"));
    }

    @Test
    @DisplayName("a precision throws away what its columns cannot change")
    void truncation() {
        var time = LocalTime.of(9, 30, 45);

        assertEquals(LocalTime.of(9, 0), TimePrecision.HOURS.truncate(time));
        assertEquals(LocalTime.of(9, 30), TimePrecision.MINUTES.truncate(time));
        assertEquals(time, TimePrecision.SECONDS.truncate(time));
    }
}
