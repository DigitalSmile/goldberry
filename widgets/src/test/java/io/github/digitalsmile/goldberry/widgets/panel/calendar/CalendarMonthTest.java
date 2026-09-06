package io.github.digitalsmile.goldberry.widgets.panel.calendar;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import java.util.Locale;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/// The grid arithmetic, with no widget — which is where "the toolkit ships no
/// calendar data of its own beyond `java.time`" is checked.
///
/// Every assertion here is against a locale rather than against a table: the
/// week's first day, the weekday names and the month name all come from
/// `java.time`, and a test that hard-coded any of them would be the calendar data
/// §10 rules out, written in a test file.
class CalendarMonthTest {

    @Test
    @DisplayName("a month is always six weeks, whatever shape it is")
    void alwaysSixWeeks() {
        for (var month = 1; month <= 12; month++) {
            var grid = CalendarMonth.of(YearMonth.of(2026, month), Locale.UK);
            assertEquals(42, grid.days().size());
        }
    }

    /// February 2026 has 28 days and starts on a Sunday, which is the shortest a
    /// month can be and the latest in the week it can start — the case that would
    /// need a seventh row if the grid were sized to its content.
    @Test
    @DisplayName("even a February that starts on a Sunday fits, in a Monday locale")
    void awkwardFebruary() {
        var grid = CalendarMonth.of(YearMonth.of(2026, 2), Locale.UK);

        assertEquals(DayOfWeek.SUNDAY, LocalDate.of(2026, 2, 1).getDayOfWeek());
        assertEquals(LocalDate.of(2026, 1, 26), grid.days().getFirst());
        assertEquals(LocalDate.of(2026, 3, 8), grid.days().getLast());
    }

    @Test
    @DisplayName("the week starts where the locale says")
    void weekStart() {
        var september = YearMonth.of(2026, 9);

        assertEquals(
                LocalDate.of(2026, 8, 31),
                CalendarMonth.of(september, Locale.UK).days().getFirst());
        assertEquals(
                LocalDate.of(2026, 8, 30),
                CalendarMonth.of(september, Locale.US).days().getFirst());
    }

    /// The one that a subtraction gets wrong: a locale whose week starts on
    /// Sunday makes `dayOfWeek - firstDayOfWeek` negative, which is why the lead
    /// is a `Math.floorMod`.
    @Test
    @DisplayName("a month beginning on the locale's first day has no lead")
    void noLeadingDays() {
        // 1 February 2026 is a Sunday, and a US week starts on one.
        var grid = CalendarMonth.of(YearMonth.of(2026, 2), Locale.US);

        assertEquals(LocalDate.of(2026, 2, 1), grid.days().getFirst());
    }

    @Test
    @DisplayName("the weekday names start on the same day the grid does")
    void weekdayNames() {
        var uk = CalendarMonth.weekdayNames(Locale.UK);
        var us = CalendarMonth.weekdayNames(Locale.US);

        assertEquals(7, uk.size());
        assertEquals(DayOfWeek.MONDAY.getDisplayName(java.time.format.TextStyle.NARROW, Locale.UK), uk.getFirst());
        assertEquals(DayOfWeek.SUNDAY.getDisplayName(java.time.format.TextStyle.NARROW, Locale.US), us.getFirst());
    }

    @Test
    @DisplayName("the label is the month's own name in the locale, and the year")
    void label() {
        var september = YearMonth.of(2026, 9);

        assertEquals("September 2026", CalendarMonth.monthLabel(september, Locale.UK));
        assertEquals(
                java.time.Month.SEPTEMBER.getDisplayName(java.time.format.TextStyle.FULL_STANDALONE, Locale.GERMANY)
                        + " 2026",
                CalendarMonth.monthLabel(september, Locale.GERMANY));
    }

    @Test
    @DisplayName("a week is seven days, and the sixth is the last")
    void weeks() {
        var grid = CalendarMonth.of(YearMonth.of(2026, 9), Locale.UK);

        assertEquals(7, grid.week(0).size());
        assertEquals(grid.days().getFirst(), grid.week(0).getFirst());
        assertEquals(grid.days().getLast(), grid.week(CalendarMonth.WEEKS - 1).getLast());
    }

    @Test
    @DisplayName("and it knows which days it borrowed")
    void borrowed() {
        var grid = CalendarMonth.of(YearMonth.of(2026, 9), Locale.UK);

        assertFalse(grid.isInMonth(LocalDate.of(2026, 8, 31)));
        assertTrue(grid.isInMonth(LocalDate.of(2026, 9, 1)));
        assertFalse(grid.isInMonth(LocalDate.of(2026, 10, 1)));
    }

    @Test
    @DisplayName("a grid of the wrong size is refused rather than drawn short")
    void wrongSize() {
        org.junit.jupiter.api.Assertions.assertThrows(
                IllegalArgumentException.class,
                () -> new CalendarMonth(YearMonth.of(2026, 9), List.of(LocalDate.of(2026, 9, 1))));
    }
}
