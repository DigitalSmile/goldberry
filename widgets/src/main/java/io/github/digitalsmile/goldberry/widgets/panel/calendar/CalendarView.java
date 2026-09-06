package io.github.digitalsmile.goldberry.widgets.panel.calendar;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.Locale;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;

import org.jspecify.annotations.Nullable;

import io.github.digitalsmile.goldberry.widget.State;
import io.github.digitalsmile.goldberry.widget.Widget;
import io.github.digitalsmile.goldberry.widget.attr.Attributed;
import io.github.digitalsmile.goldberry.widget.attr.Attributes;

/// A month grid over a date model — `docs/core-widgets.md` §10's `calendar`.
///
/// ```java
/// CalendarView.of(chosen, this::pick)
///             .between(LocalDate.now(), LocalDate.now().plusMonths(3))
///             .disabledDates(date -> date.getDayOfWeek() == DayOfWeek.SUNDAY)
/// ```
///
/// ## Why it is not called `Calendar`
///
/// `java.util.Calendar` is on every classpath and is exactly the thing this is
/// not. `ListView`/`ListBox` and `Tree`/`TreeBox` already carry the split for a
/// different reason — the CSS type is `calendar` and the class is not — and this
/// is the third widget in the group to want it.
///
/// ## Java only, like `list` and `tree`
///
/// No `@Markup`. Its model is a [Predicate], a [Function] and a [Locale], and §9
/// has no way to write any of the three: a document can name an *action* and a
/// *binding*, and a per-day cell renderer is neither. `date-picker` is the
/// markup-able half of this pair, and it configures a calendar it builds itself.
///
/// ## What §10 asks for, and what is here
///
/// Single, multiple and range selection ([DateSelection]); `min`, `max` and a
/// disabled predicate; per-day decoration; week starts and names from the
/// application's `Locale`; and the keyboard in full — arrows a day, `PgUp`/`PgDn`
/// a month, `Shift+PgUp`/`PgDn` a year, `Home`/`End` the week, with the grid as
/// one Tab stop and a roving day. The one sentence not built is the second half
/// of its semantics line: a cell's full date as its name needs a channel
/// [io.github.digitalsmile.goldberry.widget.semantics.Semantics] does not have
/// for any widget (M5).
///
/// One thing here is **not** in §10 and is written down as an addition:
/// [CalendarHeader], the month label and the two arrows, because §10 gives this
/// widget only a keyboard for changing month and a calendar a mouse cannot page
/// is not one (ADR-0274).
///
/// ## It is told what day it is, and never asks
///
/// [#month] is **required** and [#today] may be null, which is one decision
/// rather than two: `DeterminismTest` allows exactly one call to
/// `ZoneId.systemDefault()` in the whole catalog and it belongs to `TimeAxis`
/// (ADR-0203). A calendar that read the clock would be a second door, and the
/// argument against it is the same one — an instant is only a date in some zone,
/// and only the application knows which. So the month it opens on is an argument,
/// and a calendar not told what today is simply marks no day as today rather than
/// guessing at one.
///
/// It buys the thing ADR-0203 bought as well: a golden image of a month is the
/// same image tomorrow, and in Auckland.
///
/// @param selection     what is chosen, and in which of the three models
/// @param onSelect      told the whole new selection whenever it changes
/// @param month         which month to show — required, and a change pages the grid
/// @param today         which day to mark as today, or null for none
/// @param min           the earliest reachable date, or null
/// @param max           the latest reachable date, or null
/// @param disabledDates which dates in range are still refused
/// @param locale        where the week starts, and what the names are
/// @param decoration    what the application draws on a day, or null per day
/// @param disabled      whether the whole grid refuses focus and every press
/// @param attributes    the `id`, classes and key the application wrote
public record CalendarView(
        DateSelection selection,
        @Nullable Consumer<DateSelection> onSelect,
        YearMonth month,
        @Nullable LocalDate today,
        @Nullable LocalDate min,
        @Nullable LocalDate max,
        Predicate<LocalDate> disabledDates,
        Locale locale,
        Function<LocalDate, @Nullable Widget> decoration,
        boolean disabled,
        Attributes attributes)
        implements Widget.Stateful, Attributed<CalendarView> {

    /// Nothing refused beyond `min` and `max`.
    public static final Predicate<LocalDate> ALL_ALLOWED = date -> false;

    /// Nothing drawn on any day.
    public static final Function<LocalDate, @Nullable Widget> NO_DECORATION = date -> null;

    public CalendarView {
        Objects.requireNonNull(month, "month");
        selection = selection == null ? DateSelection.NONE : selection;
        disabledDates = disabledDates == null ? ALL_ALLOWED : disabledDates;
        locale = locale == null ? Locale.getDefault(Locale.Category.FORMAT) : locale;
        decoration = decoration == null ? NO_DECORATION : decoration;
        attributes = attributes == null ? Attributes.NONE : attributes;
        if (min != null && max != null && max.isBefore(min)) {
            throw new IllegalArgumentException(
                    "a calendar's max (" + max + ") is before its min (" + min + "), so no date is reachable");
        }
    }

    /// An empty single-date calendar on `month`.
    public CalendarView(YearMonth month) {
        this(DateSelection.NONE, null, month);
    }

    /// A calendar on `month`, showing `selection` and reporting every change.
    public CalendarView(DateSelection selection, @Nullable Consumer<DateSelection> onSelect, YearMonth month) {
        this(
                selection,
                onSelect,
                month,
                null,
                null,
                null,
                ALL_ALLOWED,
                Locale.getDefault(Locale.Category.FORMAT),
                NO_DECORATION,
                false,
                Attributes.NONE);
    }

    /// A calendar on the month `selection` starts in, or on `fallback` when it is
    /// empty.
    ///
    /// The common Java spelling, and the reason the fallback is an argument: this
    /// class cannot ask what month it is (see the class note), so a caller with an
    /// empty selection has to say.
    public static CalendarView of(
            DateSelection selection, @Nullable Consumer<DateSelection> onSelect, YearMonth fallback) {
        var start = selection.first();
        return new CalendarView(selection, onSelect, start == null ? fallback : YearMonth.from(start));
    }

    /// This calendar showing `shown`. A change to it pages the grid, cross-fade
    /// and all.
    public CalendarView month(YearMonth shown) {
        return new CalendarView(
                selection,
                onSelect,
                Objects.requireNonNull(shown, "shown"),
                today,
                min,
                max,
                disabledDates,
                locale,
                decoration,
                disabled,
                attributes);
    }

    /// This calendar marking `date` as today.
    ///
    /// Supplied rather than read from the clock — see the class note. Null marks
    /// no day, which is the honest answer for a widget that has not been told.
    public CalendarView today(@Nullable LocalDate date) {
        return new CalendarView(
                selection, onSelect, month, date, min, max, disabledDates, locale, decoration, disabled, attributes);
    }

    /// This calendar reaching no earlier than `from` and no later than `to`,
    /// either of which may be null.
    public CalendarView between(@Nullable LocalDate from, @Nullable LocalDate to) {
        return new CalendarView(
                selection, onSelect, month, today, from, to, disabledDates, locale, decoration, disabled, attributes);
    }

    /// This calendar refusing the dates `refused` accepts — §10's "a `disabled`
    /// predicate".
    ///
    /// Beside `min` and `max` rather than instead of them, because the two answer
    /// different questions: a bound is a window and a predicate is a rule inside
    /// it, and expressing "this quarter, weekdays only" as one predicate would
    /// make the calendar unable to say which end of the year it may page to.
    public CalendarView disabledDates(Predicate<LocalDate> refused) {
        return new CalendarView(
                selection,
                onSelect,
                month,
                today,
                min,
                max,
                Objects.requireNonNull(refused, "refused"),
                locale,
                decoration,
                disabled,
                attributes);
    }

    /// This calendar reading `locale` for its week start and its names.
    public CalendarView locale(Locale where) {
        return new CalendarView(
                selection,
                onSelect,
                month,
                today,
                min,
                max,
                disabledDates,
                Objects.requireNonNull(where, "where"),
                decoration,
                disabled,
                attributes);
    }

    /// This calendar drawing whatever `cells` returns under each day's number.
    ///
    /// §10's "per-day decoration from the application (a dot, a `badge`, a
    /// background) so an agenda or a heat map is the same widget with a different
    /// cell renderer". Returning null draws nothing, which is the common case and
    /// costs a call per visible day rather than a widget.
    public CalendarView decoration(Function<LocalDate, @Nullable Widget> cells) {
        return new CalendarView(
                selection,
                onSelect,
                month,
                today,
                min,
                max,
                disabledDates,
                locale,
                Objects.requireNonNull(cells, "cells"),
                disabled,
                attributes);
    }

    /// This calendar refusing focus and every press.
    public CalendarView disabled(boolean refused) {
        return new CalendarView(
                selection, onSelect, month, today, min, max, disabledDates, locale, decoration, refused, attributes);
    }

    @Override
    public CalendarView withAttributes(Attributes replacement) {
        return new CalendarView(
                selection, onSelect, month, today, min, max, disabledDates, locale, decoration, disabled, replacement);
    }

    @Override
    public @Nullable Object key() {
        return attributes.key();
    }

    /// Whether `date` may be reached at all — both bounds and the predicate,
    /// asked in one place so the field, the grid and the keyboard cannot
    /// disagree.
    ///
    /// §4 says this out loud for `date-picker`: "`min`, `max` and a `disabled`
    /// predicate gate both the field and the grid, so an unreachable date cannot
    /// be typed either."
    public boolean allows(LocalDate date) {
        if (min != null && date.isBefore(min)) {
            return false;
        }
        if (max != null && date.isAfter(max)) {
            return false;
        }
        return !disabledDates.test(date);
    }

    @Override
    public State<?> createState() {
        return new CalendarState();
    }
}
