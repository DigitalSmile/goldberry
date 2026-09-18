package io.github.digitalsmile.goldberry.widgets.form.datepicker;

import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.Predicate;

import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;

import io.github.digitalsmile.goldberry.bind.Observable;
import io.github.digitalsmile.goldberry.kdl.KdlNode;
import io.github.digitalsmile.goldberry.log.Logs;
import io.github.digitalsmile.goldberry.widget.State;
import io.github.digitalsmile.goldberry.widget.Widget;
import io.github.digitalsmile.goldberry.widget.attr.Attributed;
import io.github.digitalsmile.goldberry.widget.attr.Attributes;
import io.github.digitalsmile.goldberry.widget.attr.Bindable;
import io.github.digitalsmile.goldberry.widgets.markup.Markup;
import io.github.digitalsmile.goldberry.widgets.markup.Wiring;
import io.github.digitalsmile.goldberry.widgets.panel.calendar.CalendarView;
import io.github.digitalsmile.goldberry.widgets.panel.calendar.DateSelection;

/// A typed date field with a calendar under it — `docs/core-widgets.md` §4's
/// `date-picker`.
///
/// ```kdl
/// field label="Departure" {
///     date-picker bind="trip.date" change="trip.set-date" min="2026-01-01"
/// }
/// date-picker range=#true placeholder="When?"
/// ```
///
/// ```java
/// DatePicker.of(chosen, this::pick).between(today, today.plusYears(1))
/// ```
///
/// ## What it is made of
///
/// ```
/// date-picker           this node. Stateful, styles nothing, holds the text
/// └── date-picker       [DatePickerBox] — the styled node: the keys, the popover
///     ├── text-input    the field, which is a real `text-input`
///     └── date-picker-toggle  the affordance that opens the grid
/// ```
///
/// The popover holds a [CalendarView], which is §10's widget unchanged — the
/// picker configures one and does not reimplement a month.
///
/// ## The typed field is the source of truth
///
/// §4 says why, and it is the sentence the whole design follows from: "a date
/// picker you cannot type into is unusable for a birthday, and every keyboard
/// user reaches the field before the grid". So the **text** is what this holds,
/// the grid writes into it, and a value only leaves through
/// [DateFormat#parse]. A picker that held a `LocalDate` and rendered it into the
/// field would have to decide what the field says while somebody is halfway
/// through typing, and every answer to that is wrong.
///
/// ## What it will not let you reach
///
/// §4: "`min`, `max` and a `disabled` predicate gate both the field and the grid,
/// so an unreachable date cannot be typed either." One rule, asked in one place —
/// [CalendarView#allows] — by the grid that draws the day and by the field that
/// parsed it.
///
/// ## The keyboard
///
/// `Alt+Down` opens, and it is taken on the **capture** pass because the field
/// underneath would otherwise read a plain `Down` as "go to the end of the line".
/// `Esc` reverts, which is `select`'s rule for the same reason: the control holds
/// a value, typing is a way of reaching one, and abandoning the attempt must not
/// throw away something nobody asked to lose. The arrows, `PgUp` and `PgDn` reach
/// the grid without this widget forwarding anything — while a popup is open the
/// keyboard belongs to it (ADR-0104).
///
/// @param value      the text the field starts with when nothing is bound
/// @param source     the `bind=` value, or null — a `LocalDate` or its text
/// @param onChange   told the whole selection whenever a value is committed
/// @param placeholder what the field shows when it is empty
/// @param range      whether it chooses a pair — §4's `range=#true`
/// @param month      which month the grid opens on when nothing is chosen
/// @param today      which day the grid marks, or null for none
/// @param min        the earliest reachable date, or null
/// @param max        the latest reachable date, or null
/// @param disabledDates which dates in range are still refused
/// @param format     how a date is written and read
/// @param locale     what the grid's week and names are in
/// @param disabled   whether it refuses focus and matches `:disabled`
/// @param attributes the `id`, classes and key the document wrote
@Markup("date-picker")
public record DatePicker(
        String value,
        @Nullable Observable<?> source,
        @Nullable Consumer<DateSelection> onChange,
        String placeholder,
        boolean range,
        YearMonth month,
        @Nullable LocalDate today,
        @Nullable LocalDate min,
        @Nullable LocalDate max,
        Predicate<LocalDate> disabledDates,
        DateFormat format,
        Locale locale,
        boolean disabled,
        Attributes attributes)
        implements Widget.Stateful, Attributed<DatePicker>, Bindable<DatePicker> {

    private static final Logger LOG = Logs.of(DatePicker.class);

    public DatePicker {
        Objects.requireNonNull(month, "month");
        value = value == null ? "" : value;
        placeholder = placeholder == null ? "" : placeholder;
        locale = locale == null ? Locale.getDefault(Locale.Category.FORMAT) : locale;
        format = format == null ? DateFormat.of(locale) : format;
        disabledDates = disabledDates == null ? CalendarView.ALL_ALLOWED : disabledDates;
        attributes = attributes == null ? Attributes.NONE : attributes;
        if (min != null && max != null && max.isBefore(min)) {
            throw new IllegalArgumentException(
                    "a date-picker's max (" + max + ") is before its min (" + min + "), so no date is reachable");
        }
    }

    /// An empty picker whose grid opens on `month`.
    ///
    /// The month is an argument for [CalendarView]'s reason: nothing in the
    /// catalog may read the machine's time zone (ADR-0203), so a widget that
    /// opened on "this month" would be deciding what month it is.
    public DatePicker(YearMonth month) {
        this(month, null);
    }

    /// A picker on `month`, reporting every value it commits.
    public DatePicker(YearMonth month, @Nullable Consumer<DateSelection> onChange) {
        this(
                "",
                null,
                onChange,
                "",
                false,
                month,
                null,
                null,
                null,
                CalendarView.ALL_ALLOWED,
                DateFormat.of(Locale.getDefault(Locale.Category.FORMAT)),
                Locale.getDefault(Locale.Category.FORMAT),
                false,
                Attributes.NONE);
    }

    /// A picker following a property. The Java spelling of `bind=`.
    public static DatePicker of(Observable<?> source, @Nullable Consumer<DateSelection> onChange, YearMonth month) {
        return new DatePicker(
                "",
                Objects.requireNonNull(source, "source"),
                onChange,
                "",
                false,
                month,
                null,
                null,
                null,
                CalendarView.ALL_ALLOWED,
                DateFormat.of(Locale.getDefault(Locale.Category.FORMAT)),
                Locale.getDefault(Locale.Category.FORMAT),
                false,
                Attributes.NONE);
    }

    /// This picker holding `text` when nothing is bound.
    ///
    /// The *text*, not a `LocalDate`, because that is what this control holds —
    /// §4's "the typed field is the source of truth", and the reason a picker can
    /// be built showing something half typed.
    public DatePicker value(String text) {
        return new DatePicker(
                Objects.requireNonNull(text, "text"),
                source,
                onChange,
                placeholder,
                range,
                month,
                today,
                min,
                max,
                disabledDates,
                format,
                locale,
                disabled,
                attributes);
    }

    /// This picker showing `text` when it is empty.
    public DatePicker placeholder(String text) {
        return new DatePicker(
                value,
                source,
                onChange,
                text,
                range,
                month,
                today,
                min,
                max,
                disabledDates,
                format,
                locale,
                disabled,
                attributes);
    }

    /// This picker choosing a pair — §4's `range=#true`.
    public DatePicker range(boolean pair) {
        return new DatePicker(
                value,
                source,
                onChange,
                placeholder,
                pair,
                month,
                today,
                min,
                max,
                disabledDates,
                format,
                locale,
                disabled,
                attributes);
    }

    /// This picker's grid marking `date` as today.
    public DatePicker today(@Nullable LocalDate date) {
        return new DatePicker(
                value,
                source,
                onChange,
                placeholder,
                range,
                month,
                date,
                min,
                max,
                disabledDates,
                format,
                locale,
                disabled,
                attributes);
    }

    /// This picker reaching no earlier than `from` and no later than `to`.
    public DatePicker between(@Nullable LocalDate from, @Nullable LocalDate to) {
        return new DatePicker(
                value,
                source,
                onChange,
                placeholder,
                range,
                month,
                today,
                from,
                to,
                disabledDates,
                format,
                locale,
                disabled,
                attributes);
    }

    /// This picker refusing the dates `refused` accepts, inside its bounds.
    public DatePicker disabledDates(Predicate<LocalDate> refused) {
        return new DatePicker(
                value,
                source,
                onChange,
                placeholder,
                range,
                month,
                today,
                min,
                max,
                Objects.requireNonNull(refused, "refused"),
                format,
                locale,
                disabled,
                attributes);
    }

    /// This picker writing and reading dates with `how` — §4's "a `java.time`
    /// formatter the application supplies".
    public DatePicker format(DateFormat how) {
        return new DatePicker(
                value,
                source,
                onChange,
                placeholder,
                range,
                month,
                today,
                min,
                max,
                disabledDates,
                Objects.requireNonNull(how, "how"),
                locale,
                disabled,
                attributes);
    }

    /// This picker reading `where` for its grid's week and names.
    ///
    /// It does **not** change [#format], because the two are separate answers: an
    /// application showing ISO dates to a French user is showing ISO dates, and a
    /// locale that quietly replaced the formatter would undo that.
    public DatePicker locale(Locale where) {
        return new DatePicker(
                value,
                source,
                onChange,
                placeholder,
                range,
                month,
                today,
                min,
                max,
                disabledDates,
                format,
                Objects.requireNonNull(where, "where"),
                disabled,
                attributes);
    }

    /// This picker refusing focus and every press.
    public DatePicker disabled(boolean refused) {
        return new DatePicker(
                value,
                source,
                onChange,
                placeholder,
                range,
                month,
                today,
                min,
                max,
                disabledDates,
                format,
                locale,
                refused,
                attributes);
    }

    @Override
    public DatePicker withAttributes(Attributes replacement) {
        return new DatePicker(
                value,
                source,
                onChange,
                placeholder,
                range,
                month,
                today,
                min,
                max,
                disabledDates,
                format,
                locale,
                disabled,
                replacement);
    }

    @Override
    public DatePicker bound(Observable<?> binding) {
        return new DatePicker(
                value,
                binding,
                onChange,
                placeholder,
                range,
                month,
                today,
                min,
                max,
                disabledDates,
                format,
                locale,
                disabled,
                attributes);
    }

    @Override
    public @Nullable Observable<?> binding() {
        return source;
    }

    @Override
    public @Nullable Object key() {
        return attributes.key();
    }

    /// Which of §10's three models this picker's grid is in.
    public DateSelection.Mode mode() {
        return range ? DateSelection.Mode.RANGE : DateSelection.Mode.SINGLE;
    }

    /// What the picker starts from, as text.
    ///
    /// **A binding may hold either a `LocalDate` or a string**, and both are
    /// meant: `bind=` reads whatever the application's model holds, and a model
    /// that keeps dates as dates is the common case while one that keeps them as
    /// text is a form that has not parsed them yet. A `LocalDate` is *formatted*
    /// so the field shows the application's own spelling; anything else is taken
    /// as text it typed itself.
    public String resolved() {
        if (source == null) {
            return value;
        }
        var current = source.get();
        return switch (current) {
            case null -> "";
            case LocalDate date -> format.format(date);
            case DateSelection selection -> format.format(selection);
            default -> String.valueOf(current);
        };
    }

    /// Whether `date` may be reached — the one rule §4 asks the field and the
    /// grid to share.
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
        return new DatePickerState();
    }

    /// Builds a `date-picker` from markup.
    ///
    /// `min=` and `max=` are **ISO** — `2026-01-01` — and are the one place this
    /// widget names a date syntax. §4's "the toolkit does not invent a date
    /// syntax" is about what a *user* types, which is [#format]'s and the
    /// application's; a bound written into a document is written by a programmer,
    /// and `LocalDate.parse`'s own format is the one every JVM reads the same way
    /// whatever locale it started in.
    ///
    /// The formatter itself is deliberately absent: §4 says the application
    /// supplies it, and a `DateTimeFormatter` is not something KDL can carry. A
    /// document therefore gets the locale's short form, which is the documented
    /// default rather than a gap.
    public static Widget inflate(KdlNode node, List<Widget> children, Wiring wiring) {
        var format = DateFormat.of(Locale.getDefault(Locale.Category.FORMAT));
        var reported = wiring.valued(node, "change");
        // **Read once.** `monthOf` wants the same bound to open on, and asking for
        // it a second time parsed `min=` twice -- so a malformed one was complained
        // about twice for one attribute, which reads as two mistakes.
        var min = isoDate(node, "min");
        return new DatePicker(
                Objects.requireNonNullElse(node.stringProperty("value"), ""),
                wiring.bound(node),
                // A document's `change` carries **text**, because §9's valued
                // actions cross as a `String` and nothing else -- so a document
                // is told the formatted date and Java is told the `DateSelection`.
                // That is the same split `text-input` has, one level up: what a
                // document can be handed is what a document can name.
                reported == null ? null : value -> reported.accept(format.format(value)),
                Objects.requireNonNullElse(node.stringProperty("placeholder"), ""),
                node.booleanProperty("range"),
                monthOf(node, min),
                isoDate(node, "today"),
                min,
                isoDate(node, "max"),
                CalendarView.ALL_ALLOWED,
                format,
                Locale.getDefault(Locale.Category.FORMAT),
                Wiring.disabled(node),
                Attributes.of(node));
    }

    /// Which month a document's picker opens on.
    ///
    /// `month="2026-09"` when it says so, the `min` when it does not, and
    /// otherwise the month of the epoch — which is visibly wrong rather than
    /// quietly wrong, and is the honest answer for a widget that may not read a
    /// clock. A document that wants this month writes it, or binds a value.
    ///
    /// @param min what [#inflate] read out of `min=`, handed in rather than read
    ///        again: parsing it twice complained about a malformed one twice
    private static YearMonth monthOf(KdlNode node, @Nullable LocalDate min) {
        var written = node.stringProperty("month");
        if (written != null && !written.isEmpty()) {
            try {
                return YearMonth.parse(written);
            } catch (DateTimeParseException refused) {
                LOG.warn("date-picker month=\"{}\" is not an ISO year-month like 2026-09; ignoring it", written);
            }
        }
        return min != null ? YearMonth.from(min) : YearMonth.from(LocalDate.EPOCH);
    }

    private static @Nullable LocalDate isoDate(KdlNode node, String property) {
        var written = node.stringProperty(property);
        if (written == null || written.isEmpty()) {
            return null;
        }
        try {
            return LocalDate.parse(written);
        } catch (DateTimeParseException refused) {
            // Logged rather than thrown, exactly as an unknown `filter=` is: it is
            // a typo already visible in the markup, and a picker that refused
            // every date is a worse way to find out.
            LOG.warn("date-picker {}=\"{}\" is not an ISO date like 2026-01-31; ignoring it", property, written);
            return null;
        }
    }
}
