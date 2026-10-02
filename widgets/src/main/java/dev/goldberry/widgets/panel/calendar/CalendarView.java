package dev.goldberry.widgets.panel.calendar;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.Locale;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;

import org.jspecify.annotations.Nullable;

import dev.goldberry.widget.State;
import dev.goldberry.widget.Widget;
import dev.goldberry.widget.attr.Attributed;
import dev.goldberry.widget.attr.Attributes;

/// A month grid over a date model: the `calendar` a `date-picker` opens, usable
/// on its own from Java.
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
/// No `@Markup`. Its model is a [Predicate], a [Function] and a [Locale], and
/// markup has no way to write any of the three: a document can name an *action*
/// and a *binding*, and a per-day cell renderer is neither. `date-picker` is the
/// markup-able half of this pair, and it configures a calendar it builds itself.
///
/// ## What it does
///
/// Single, multiple and range selection ([DateSelection]); `min`, `max` and a
/// disabled predicate; per-day decoration; week starts and names from the
/// application's `Locale`; and the keyboard in full — arrows a day, `PgUp`/`PgDn`
/// a month, `Shift+PgUp`/`PgDn` a year, `Home`/`End` the week, with the grid as
/// one Tab stop and a roving day. A cell's full date as its accessible name is
/// the one thing missing: it needs a channel
/// [dev.goldberry.widget.semantics.Semantics] does not have for any widget.
///
/// [CalendarHeader], the month label and the two arrows, is there because the
/// keyboard alone can page a month and a mouse cannot; a calendar a mouse cannot
/// page is not one.
///
/// ## It is told what day it is, and never asks
///
/// [#month] is required and [#today] may be null, which is one decision rather
/// than two. The toolkit reads the system zone in exactly one place, the chart
/// time axis, and a calendar that read the clock would be a second door. The
/// argument against it is the same: an instant is only a date in some zone, and
/// only the application knows which. So the month it opens on is an argument,
/// and a calendar not told what today is marks no day as today rather than
/// guessing at one. A golden image of a month is then the same image tomorrow,
/// and in Auckland.
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
///
/// Read more: [Fields and forms](https://goldberry.dev/docs/components/forms.html#date-picker).
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

    /// Written out so that the parameters taking null for a default can say so.
    public CalendarView(
            @Nullable DateSelection selection,
            @Nullable Consumer<DateSelection> onSelect,
            YearMonth month,
            @Nullable LocalDate today,
            @Nullable LocalDate min,
            @Nullable LocalDate max,
            @Nullable Predicate<LocalDate> disabledDates,
            @Nullable Locale locale,
            @Nullable Function<LocalDate, @Nullable Widget> decoration,
            boolean disabled,
            @Nullable Attributes attributes) {
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
        this.selection = selection;
        this.onSelect = onSelect;
        this.month = month;
        this.today = today;
        this.min = min;
        this.max = max;
        this.disabledDates = disabledDates;
        this.locale = locale;
        this.decoration = decoration;
        this.disabled = disabled;
        this.attributes = attributes;
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

    /// This calendar refusing the dates `refused` accepts: the disabled
    /// predicate.
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
    /// Per-day decoration from the application — a dot, a `badge`, a background —
    /// so an agenda or a heat map is the same widget with a different cell
    /// renderer. Returning null draws nothing, which is the common case and
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
    /// For a `date-picker`, `min`, `max` and the disabled predicate gate both the
    /// field and the grid, so an unreachable date cannot be typed either.
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
