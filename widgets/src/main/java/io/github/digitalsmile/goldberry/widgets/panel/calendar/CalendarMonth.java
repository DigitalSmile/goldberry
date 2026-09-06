package io.github.digitalsmile.goldberry.widgets.panel.calendar;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.TextStyle;
import java.time.temporal.WeekFields;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

/// The grid a month is drawn as, in one locale — the arithmetic, with no widget
/// around it.
///
/// **Six rows of seven, always.** A month occupies four to six weeks depending on
/// its length and which day it starts on, and a grid that changed height between
/// them would move everything under it — a popover would resize under the
/// pointer, and §3.1's month cross-fade would be a cross-fade between two shapes.
/// So the grid is the maximum and the spare days come from the months on either
/// side, which are drawn quieter and are still real dates a user may press. That
/// is what every calendar does and it is not in §10, which says only "a month
/// grid".
///
/// ## The locale decides where the week starts
///
/// §10: "Week starts and month/day names come from the `Locale` the application
/// supplies — the toolkit ships no calendar data of its own beyond `java.time`."
/// So this holds no table of anything. [WeekFields#of(Locale)] answers Monday for
/// most of Europe and Sunday for the United States, and
/// [DayOfWeek#getDisplayName] and [java.time.Month#getDisplayName] write the
/// names.
///
/// @param month the month this is a grid of
/// @param days  42 dates, in reading order, starting on the locale's first day of
///              the week
public record CalendarMonth(YearMonth month, List<LocalDate> days) {

    /// Rows in the grid. See the class note — always six.
    public static final int WEEKS = 6;

    /// Days in a week, which is the one number here that no locale changes.
    public static final int DAYS_IN_WEEK = 7;

    public CalendarMonth {
        Objects.requireNonNull(month, "month");
        days = List.copyOf(days);
        if (days.size() != WEEKS * DAYS_IN_WEEK) {
            throw new IllegalArgumentException(
                    "a month grid is " + WEEKS * DAYS_IN_WEEK + " days and " + days.size() + " were given");
        }
    }

    /// The grid for `month`, laid out for `locale`.
    public static CalendarMonth of(YearMonth month, Locale locale) {
        Objects.requireNonNull(month, "month");
        Objects.requireNonNull(locale, "locale");
        var firstDayOfWeek = WeekFields.of(locale).getFirstDayOfWeek();
        var first = month.atDay(1);
        // How far back to reach for the first cell: the number of days from the
        // week's first day to this month's first day, which is 0 when the month
        // begins on it. `Math.floorMod` rather than a subtraction and an `if`,
        // because DayOfWeek's values run Monday..Sunday and a locale starting on
        // Sunday makes the difference negative.
        var lead = Math.floorMod(first.getDayOfWeek().getValue() - firstDayOfWeek.getValue(), DAYS_IN_WEEK);
        var start = first.minusDays(lead);
        var days = new ArrayList<LocalDate>(WEEKS * DAYS_IN_WEEK);
        for (var i = 0; i < WEEKS * DAYS_IN_WEEK; i++) {
            days.add(start.plusDays(i));
        }
        return new CalendarMonth(month, days);
    }

    /// The seven weekday names for the header row, starting on the locale's first
    /// day of the week.
    ///
    /// [TextStyle#NARROW], which is what a 32-point cell has room for — "M" in
    /// English, "月" in Japanese. The locale decides whether that is one letter or
    /// two, and a toolkit that truncated to one would break the locales where it
    /// is not.
    public static List<String> weekdayNames(Locale locale) {
        Objects.requireNonNull(locale, "locale");
        var firstDayOfWeek = WeekFields.of(locale).getFirstDayOfWeek();
        var names = new ArrayList<String>(DAYS_IN_WEEK);
        for (var i = 0; i < DAYS_IN_WEEK; i++) {
            names.add(firstDayOfWeek.plus(i).getDisplayName(TextStyle.NARROW, locale));
        }
        return List.copyOf(names);
    }

    /// What the header says — the month and the year, in `locale`.
    ///
    /// The month's full name and the year with a space between them, which is not
    /// a format `java.time` offers and is the one string here the toolkit does
    /// write. `MMMM yyyy` through a `DateTimeFormatter` would be a *pattern*, and
    /// a pattern is a syntax the toolkit would be inventing for locales whose
    /// order it has not checked. An application that wants otherwise supplies a
    /// header of its own — which is what the per-day decoration seam is for one
    /// row down.
    public static String monthLabel(YearMonth month, Locale locale) {
        Objects.requireNonNull(month, "month");
        Objects.requireNonNull(locale, "locale");
        return month.getMonth().getDisplayName(TextStyle.FULL_STANDALONE, locale) + " " + month.getYear();
    }

    /// The `index`-th week, seven days long.
    public List<LocalDate> week(int index) {
        Objects.checkIndex(index, WEEKS);
        return days.subList(index * DAYS_IN_WEEK, (index + 1) * DAYS_IN_WEEK);
    }

    /// Whether `date` belongs to the month this is a grid *of*, rather than to
    /// one of the two it borrows from.
    public boolean isInMonth(LocalDate date) {
        return YearMonth.from(date).equals(month);
    }
}
