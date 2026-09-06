package io.github.digitalsmile.goldberry.widgets.form.timepicker;

import java.time.LocalTime;
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

/// A typed time field with hour/minute/second wheels under it —
/// `docs/core-widgets.md` §4's `time-picker`.
///
/// ```kdl
/// field label="Starts at" {
///     time-picker bind="meeting.time" change="meeting.set-time" min="09:00" max="17:00"
/// }
/// time-picker precision="seconds"
/// ```
///
/// ```java
/// TimePicker.of(start, this::pick).between(LocalTime.of(9, 0), LocalTime.of(17, 0))
/// ```
///
/// ## What it is made of
///
/// ```
/// time-picker           this node. Stateful, styles nothing, holds the text
/// └── time-picker       [io.github.digitalsmile.goldberry.widgets.form.parts.PickerField]
///     ├── text-input    the field, which is a real `text-input`
///     └── picker-toggle the affordance that opens the wheels
/// ```
///
/// **`date-picker`'s widget, with a different popover.** The field, the
/// affordance, `Alt+Down`, `Esc`, the delegated focus and the `:checked`
/// affordance are all the same node — `PickerField` — because §4 writes the two
/// pickers in one entry and they differ in exactly one thing: what is inside the
/// popup. This one's is [TimeColumns].
///
/// ## The typed field is the source of truth
///
/// §4's sentence covers both pickers and this one obeys it identically: the
/// **text** is what this holds, the wheels write into it, and a value only leaves
/// through [TimeFormat#parse]. See `DatePicker` for the argument.
///
/// ## What it will not let you reach
///
/// `min`, `max` and a disabled predicate, asked in one place — [#allows] — by the
/// field that parsed a time and by the wheels before they turn onto one. §4's
/// "an unreachable date cannot be typed either", for times.
///
/// **`min` and `max` are inclusive and do not wrap.** A shift that runs from 22:00
/// to 06:00 is two ranges rather than one, and a picker that let a bound wrap
/// would have no way to say which of the two a time at 03:00 was in. An
/// application with a night shift supplies a predicate, which can say it.
///
/// @param value      the text the field starts with when nothing is bound
/// @param source     the `bind=` value, or null — a `LocalTime` or its text
/// @param onChange   told the time whenever one is committed, or null when cleared
/// @param placeholder what the field shows when it is empty
/// @param precision  how many wheels there are
/// @param fallback   where the wheels start when nothing is chosen
/// @param min        the earliest reachable time, or null
/// @param max        the latest reachable time, or null
/// @param disabledTimes which times in range are still refused
/// @param format     how a time is written and read, or null for the locale's
///                   default at this [#precision]
/// @param disabled   whether it refuses focus and matches `:disabled`
/// @param attributes the `id`, classes and key the document wrote
@Markup("time-picker")
public record TimePicker(
        String value,
        @Nullable Observable<?> source,
        @Nullable Consumer<@Nullable LocalTime> onChange,
        String placeholder,
        TimePrecision precision,
        LocalTime fallback,
        @Nullable LocalTime min,
        @Nullable LocalTime max,
        Predicate<LocalTime> disabledTimes,
        @Nullable TimeFormat format,
        boolean disabled,
        Attributes attributes)
        implements Widget.Stateful, Attributed<TimePicker>, Bindable<TimePicker> {

    private static final Logger LOG = Logs.of(TimePicker.class);

    /// Nothing refused beyond `min` and `max`.
    public static final Predicate<LocalTime> ALL_ALLOWED = time -> false;

    /// Where the wheels start when nothing is chosen and nobody said otherwise.
    ///
    /// Midnight, and **not** the current time: nothing in the catalog may read the
    /// machine's time zone (ADR-0203), and a picker that opened on "now" would be
    /// deciding what time it is. An application that wants a sensible starting
    /// point passes one, which is the same shape `CalendarView` gives a month.
    public static final LocalTime DEFAULT_FALLBACK = LocalTime.MIDNIGHT;

    public TimePicker {
        value = value == null ? "" : value;
        placeholder = placeholder == null ? "" : placeholder;
        precision = precision == null ? TimePrecision.MINUTES : precision;
        fallback = fallback == null ? DEFAULT_FALLBACK : fallback;
        disabledTimes = disabledTimes == null ? ALL_ALLOWED : disabledTimes;
        attributes = attributes == null ? Attributes.NONE : attributes;
        if (min != null && max != null && max.isBefore(min)) {
            throw new IllegalArgumentException("a time-picker's max (" + max + ") is before its min (" + min
                    + "); a range that wraps past midnight is two ranges, and a predicate is what says so");
        }
    }

    /// An empty picker showing hours and minutes.
    public TimePicker() {
        this(null);
    }

    /// A picker reporting every time it commits.
    public TimePicker(@Nullable Consumer<@Nullable LocalTime> onChange) {
        this(
                "",
                null,
                onChange,
                "",
                TimePrecision.MINUTES,
                DEFAULT_FALLBACK,
                null,
                null,
                ALL_ALLOWED,
                null,
                false,
                Attributes.NONE);
    }

    /// A picker following a property. The Java spelling of `bind=`.
    public static TimePicker of(Observable<?> source, @Nullable Consumer<@Nullable LocalTime> onChange) {
        return new TimePicker(onChange).bound(Objects.requireNonNull(source, "source"));
    }

    /// This picker holding `text` when nothing is bound.
    ///
    /// The *text*, not a `LocalTime`, because that is what this control holds —
    /// §4's "the typed field is the source of truth", and the reason a picker can
    /// be built showing something half typed.
    public TimePicker value(String text) {
        return new TimePicker(
                Objects.requireNonNull(text, "text"),
                source,
                onChange,
                placeholder,
                precision,
                fallback,
                min,
                max,
                disabledTimes,
                format,
                disabled,
                attributes);
    }

    /// This picker showing `text` when it is empty.
    public TimePicker placeholder(String text) {
        return new TimePicker(
                value,
                source,
                onChange,
                text,
                precision,
                fallback,
                min,
                max,
                disabledTimes,
                format,
                disabled,
                attributes);
    }

    /// This picker with `wheels` many columns.
    ///
    /// The **format follows**, unless one was supplied — and it follows because
    /// [#format] is null until somebody sets it rather than because this method
    /// reaches over and rewrites it. That distinction is `WidgetWitherTest`'s and
    /// it is a real one: a wither that changed a second component would make
    /// `precision(its own value)` produce a record unequal to the one it came
    /// from, since a `DateTimeFormatter` has no value equality and two built the
    /// same way are two objects.
    ///
    /// A picker that grew a seconds column and kept a field that cannot show one
    /// would be a control whose two halves disagree; see [TimeFormat] for why the
    /// default differs by precision at all.
    public TimePicker precision(TimePrecision wheels) {
        Objects.requireNonNull(wheels, "wheels");
        return new TimePicker(
                value,
                source,
                onChange,
                placeholder,
                wheels,
                fallback,
                min,
                max,
                disabledTimes,
                format,
                disabled,
                attributes);
    }

    /// This picker whose wheels start at `time` when nothing is chosen.
    public TimePicker fallback(LocalTime time) {
        return new TimePicker(
                value,
                source,
                onChange,
                placeholder,
                precision,
                Objects.requireNonNull(time, "time"),
                min,
                max,
                disabledTimes,
                format,
                disabled,
                attributes);
    }

    /// This picker reaching no earlier than `from` and no later than `to`.
    public TimePicker between(@Nullable LocalTime from, @Nullable LocalTime to) {
        return new TimePicker(
                value,
                source,
                onChange,
                placeholder,
                precision,
                fallback,
                from,
                to,
                disabledTimes,
                format,
                disabled,
                attributes);
    }

    /// This picker refusing the times `refused` accepts, inside its bounds.
    public TimePicker disabledTimes(Predicate<LocalTime> refused) {
        return new TimePicker(
                value,
                source,
                onChange,
                placeholder,
                precision,
                fallback,
                min,
                max,
                Objects.requireNonNull(refused, "refused"),
                format,
                disabled,
                attributes);
    }

    /// This picker writing and reading times with `how`, or with the locale's
    /// default for its precision when `how` is null.
    ///
    /// Pinning it means [#precision] no longer moves it, which is the point: an
    /// application that supplied a formatter chose one. Null puts it back, which
    /// is what makes this wither able to take its own value — the thing
    /// `WidgetWitherTest` checks of every one of them.
    public TimePicker format(@Nullable TimeFormat how) {
        return new TimePicker(
                value,
                source,
                onChange,
                placeholder,
                precision,
                fallback,
                min,
                max,
                disabledTimes,
                how,
                disabled,
                attributes);
    }

    /// This picker refusing focus and every press.
    public TimePicker disabled(boolean refused) {
        return new TimePicker(
                value,
                source,
                onChange,
                placeholder,
                precision,
                fallback,
                min,
                max,
                disabledTimes,
                format,
                refused,
                attributes);
    }

    @Override
    public TimePicker withAttributes(Attributes replacement) {
        return new TimePicker(
                value,
                source,
                onChange,
                placeholder,
                precision,
                fallback,
                min,
                max,
                disabledTimes,
                format,
                disabled,
                replacement);
    }

    @Override
    public TimePicker bound(Observable<?> binding) {
        return new TimePicker(
                value,
                binding,
                onChange,
                placeholder,
                precision,
                fallback,
                min,
                max,
                disabledTimes,
                format,
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

    /// How this picker writes and reads a time — its own formatter, or the
    /// locale's default for its precision.
    ///
    /// The derivation lives here rather than in the constructor so that
    /// [#precision] can change one component and still move the format with it;
    /// see that method.
    public TimeFormat resolvedFormat() {
        return format == null ? TimeFormat.of(Locale.getDefault(Locale.Category.FORMAT), precision) : format;
    }

    /// What the picker starts from, as text.
    ///
    /// A binding may hold either a `LocalTime` or a string, and both are meant —
    /// `DatePicker#resolved` gives the argument.
    public String resolved() {
        if (source == null) {
            return value;
        }
        var current = source.get();
        return switch (current) {
            case null -> "";
            case LocalTime time -> resolvedFormat().format(precision.truncate(time));
            default -> String.valueOf(current);
        };
    }

    /// Whether `time` may be reached — the one rule the field and the wheels
    /// share.
    public boolean allows(LocalTime time) {
        var at = precision.truncate(time);
        if (min != null && at.isBefore(precision.truncate(min))) {
            return false;
        }
        if (max != null && at.isAfter(precision.truncate(max))) {
            return false;
        }
        return !disabledTimes.test(at);
    }

    @Override
    public State<?> createState() {
        return new TimePickerState();
    }

    /// Builds a `time-picker` from markup.
    ///
    /// `min=`, `max=` and `fallback=` are **ISO** — `09:00`, `17:30:15` — for
    /// `date-picker`'s stated reason: §4's "the toolkit does not invent a date
    /// syntax" is about what a *user* types, and a bound written into a document
    /// is written by a programmer.
    public static Widget inflate(KdlNode node, List<Widget> children, Wiring wiring) {
        var precision = precisionOf(node);
        // Resolved here only to format what `change` reports; the widget is given
        // null, so a document's picker still follows its precision.
        var format = TimeFormat.of(Locale.getDefault(Locale.Category.FORMAT), precision);
        var reported = wiring.valued(node, "change");
        return new TimePicker(
                Objects.requireNonNullElse(node.stringProperty("value"), ""),
                wiring.bound(node),
                // A document's `change` carries **text**, because §9's valued
                // actions cross as a `String` and nothing else. `date-picker`
                // makes the same split for the same reason.
                reported == null ? null : time -> reported.accept(format.format(time)),
                Objects.requireNonNullElse(node.stringProperty("placeholder"), ""),
                precision,
                Objects.requireNonNullElse(isoTime(node, "fallback"), DEFAULT_FALLBACK),
                isoTime(node, "min"),
                isoTime(node, "max"),
                ALL_ALLOWED,
                null,
                Wiring.disabled(node),
                Attributes.of(node));
    }

    private static TimePrecision precisionOf(KdlNode node) {
        var name = node.stringProperty("precision");
        if (name == null || name.isEmpty()) {
            return TimePrecision.MINUTES;
        }
        return switch (name.toLowerCase(Locale.ROOT)) {
            case "hours", "hour" -> TimePrecision.HOURS;
            case "minutes", "minute" -> TimePrecision.MINUTES;
            case "seconds", "second" -> TimePrecision.SECONDS;
            default -> {
                LOG.warn(
                        "time-picker precision=\"{}\" names no precision this toolkit has;"
                                + " the picker will show hours and minutes",
                        name);
                yield TimePrecision.MINUTES;
            }
        };
    }

    private static @Nullable LocalTime isoTime(KdlNode node, String property) {
        var written = node.stringProperty(property);
        if (written == null || written.isEmpty()) {
            return null;
        }
        try {
            return LocalTime.parse(written);
        } catch (DateTimeParseException refused) {
            LOG.warn("time-picker {}=\"{}\" is not an ISO time like 09:00; ignoring it", property, written);
            return null;
        }
    }
}
