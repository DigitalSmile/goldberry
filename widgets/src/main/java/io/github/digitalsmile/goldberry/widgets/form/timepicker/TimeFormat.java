package io.github.digitalsmile.goldberry.widgets.form.timepicker;

import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.format.FormatStyle;
import java.util.Locale;
import java.util.Objects;

import org.jspecify.annotations.Nullable;

/// What a [TimePicker]'s field says, and what it makes of what was typed into it.
///
/// `DateFormat`'s shape and its argument unchanged — §4 puts formatting and
/// parsing on "a `java.time` formatter the application supplies, defaulting to
/// the locale's short form", and says in the same breath that the toolkit does
/// not invent a syntax. So there is no pattern written here, one
/// [DateTimeFormatter] used in both directions, and no range: a time picker
/// chooses one time.
///
/// ## The default is the locale's, and it is a shorter default than a date's
///
/// [FormatStyle#SHORT] on a time is hours and minutes in most locales and never
/// seconds, which is right for [TimePrecision#MINUTES] and wrong for
/// [TimePrecision#SECONDS] — a picker with a seconds column whose field cannot
/// show one. So the default is chosen **per precision**: the locale's short form
/// for hours and minutes, and `HH:mm:ss` when there is a seconds column, which is
/// the one place this class writes a pattern and does so only because no
/// `FormatStyle` produces a time with seconds and without a time zone.
///
/// An application that wants otherwise supplies a formatter, which is what §4
/// says it does.
///
/// @param formatter how a time is written and read
public record TimeFormat(DateTimeFormatter formatter) {

    /// The pattern used when there is a seconds column — see the class note.
    private static final String WITH_SECONDS = "HH:mm:ss";

    public TimeFormat {
        Objects.requireNonNull(formatter, "formatter");
    }

    /// §4's default, for a picker at `precision`.
    public static TimeFormat of(Locale locale, TimePrecision precision) {
        Objects.requireNonNull(locale, "locale");
        Objects.requireNonNull(precision, "precision");
        return new TimeFormat(
                precision.hasSeconds()
                        ? DateTimeFormatter.ofPattern(WITH_SECONDS, locale)
                        : DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT).withLocale(locale));
    }

    /// An application's own formatter, for both directions.
    public static TimeFormat of(DateTimeFormatter formatter) {
        return new TimeFormat(formatter);
    }

    /// What the field shows for `time` — empty when nothing is chosen.
    public String format(@Nullable LocalTime time) {
        return time == null ? "" : formatter.format(time);
    }

    /// What `text` means, or **null when it means nothing**.
    ///
    /// `DateFormat`'s three answers, unchanged, and for its reason: blank is an
    /// empty value and rubbish is null, because the field acts differently on
    /// each — an empty field clears the value and a half-typed one leaves the last
    /// good one alone. The two are told apart by the caller, which reads a null
    /// as "not yet" and an empty [java.util.Optional] as "cleared".
    ///
    /// @return the time, or null when the text is neither blank nor a time
    public @Nullable LocalTime parse(String text) {
        Objects.requireNonNull(text, "text");
        var trimmed = text.trim();
        if (trimmed.isEmpty()) {
            return null;
        }
        try {
            return LocalTime.parse(trimmed, formatter);
        } catch (DateTimeParseException refused) {
            // Not logged, for `DateFormat`'s reason: a half-typed time fails on
            // every keystroke and a field that warned about each would fill a log
            // with the user typing.
            return null;
        }
    }

    /// Whether `text` is blank, which is the answer that means "cleared" rather
    /// than "not a time yet".
    public static boolean isBlank(String text) {
        return text.trim().isEmpty();
    }
}
