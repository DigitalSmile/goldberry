package io.github.digitalsmile.goldberry.widgets.form.datepicker;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.format.FormatStyle;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

import org.jspecify.annotations.Nullable;

import io.github.digitalsmile.goldberry.widgets.panel.calendar.DateSelection;

/// What a [DatePicker]'s field says, and what it makes of what was typed into it.
///
/// §4 is emphatic about who owns this: "Parsing and formatting take a
/// `java.time` formatter the application supplies, defaulting to the locale's
/// short form — **the toolkit does not invent a date syntax**." So there is no
/// pattern written down here and no list of accepted spellings. There is one
/// [DateTimeFormatter], used in both directions, and it came from
/// [DateTimeFormatter#ofLocalizedDate] unless somebody said otherwise.
///
/// ## The one syntax this does invent, and why it had to
///
/// A range has two dates and something between them, and no locale service
/// answers "how does this language join two dates". §4 asks for
/// `date-picker range=#true` to select a pair and "report it as one value", so
/// the pair has to be one string in one field, and something has to separate
/// them.
///
/// It is an **en dash with spaces** — `1 Sep 2026 – 21 Sep 2026` — which is what
/// a range is typeset as in English and is not a character any locale's date
/// format contains. Parsing is looser than formatting, and deliberately: a plain
/// hyphen is accepted too, because it is what a keyboard has. That looseness is
/// the reason the separator cannot be a hyphen on its own — `9-1-2026` is a date
/// in some locales, and a separator that could also be inside a date is one that
/// makes a range unparseable.
///
/// @param formatter how a single date is written and read
/// @param separator what joins the two dates of a range
public record DateFormat(DateTimeFormatter formatter, String separator) {

    /// What a range is written with — see the class note.
    public static final String RANGE_SEPARATOR = " – ";

    /// The spellings of [#RANGE_SEPARATOR] a user may type, longest first so that
    /// a spaced dash is not split on its own dash.
    private static final List<String> ACCEPTED_SEPARATORS = List.of(" – ", " - ", "–", " to ");

    public DateFormat {
        Objects.requireNonNull(formatter, "formatter");
        Objects.requireNonNull(separator, "separator");
    }

    /// §4's default: "the locale's short form".
    public static DateFormat of(Locale locale) {
        Objects.requireNonNull(locale, "locale");
        return new DateFormat(
                DateTimeFormatter.ofLocalizedDate(FormatStyle.SHORT).withLocale(locale), RANGE_SEPARATOR);
    }

    /// An application's own formatter, for both directions.
    public static DateFormat of(DateTimeFormatter formatter) {
        return new DateFormat(formatter, RANGE_SEPARATOR);
    }

    /// What the field shows for `selection` — empty when nothing is chosen.
    public String format(DateSelection selection) {
        var start = selection.first();
        if (start == null) {
            return "";
        }
        var end = selection.last();
        return end == null ? format(start) : format(start) + separator + format(end);
    }

    /// One date, written.
    public String format(LocalDate date) {
        return formatter.format(date);
    }

    /// What `text` means, in `mode` — or **null when it means nothing**.
    ///
    /// Null rather than an exception or an empty selection, because the three
    /// answers are genuinely different and the field acts differently on each: a
    /// parse that failed leaves the last committed value alone, and an *empty*
    /// field clears it. Blank is therefore an empty selection and rubbish is
    /// null.
    public @Nullable DateSelection parse(String text, DateSelection.Mode mode) {
        Objects.requireNonNull(text, "text");
        var trimmed = text.trim();
        if (trimmed.isEmpty()) {
            return DateSelection.empty(mode);
        }
        if (mode != DateSelection.Mode.RANGE) {
            var date = parseDate(trimmed);
            return date == null ? null : new DateSelection(mode, List.of(date));
        }
        for (var candidate : ACCEPTED_SEPARATORS) {
            var at = trimmed.indexOf(candidate);
            if (at <= 0) {
                continue;
            }
            var start = parseDate(trimmed.substring(0, at).trim());
            var end = parseDate(trimmed.substring(at + candidate.length()).trim());
            if (start != null && end != null) {
                return DateSelection.range(start, end);
            }
        }
        // A range field holding one date is a range half chosen, which is a real
        // state: it is what the field says between the two presses in the grid,
        // and refusing it would make the first half of a typed range vanish.
        var single = parseDate(trimmed);
        return single == null ? null : DateSelection.range(single, null);
    }

    private @Nullable LocalDate parseDate(String text) {
        try {
            return LocalDate.parse(text, formatter);
        } catch (DateTimeParseException refused) {
            // Not logged. A half-typed date fails to parse on every keystroke,
            // and a field that warned about each would fill a log with the user
            // typing.
            return null;
        }
    }
}
