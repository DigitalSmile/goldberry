package io.github.digitalsmile.goldberry.widgets.panel.calendar;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import org.jspecify.annotations.Nullable;

/// What a [CalendarView] has chosen — §10's "single date, multiple dates, or a
/// range".
///
/// **A value**, and the whole of what a press on a day means. Every rule about
/// what clicking a day does is [#with], testable with no widget, no font and no
/// frame — `TextEdit`'s arrangement and for its reason.
///
/// ## One type for three models, and why it is not three
///
/// `list` answers the same question with a [io.github.digitalsmile.goldberry.widgets.panel.list.Selection]
/// enum and a separate set of chosen rows, and that split works there because a
/// list's three models differ only in *how many* rows may be chosen. A
/// calendar's third model is not a count: a range of two dates is not two dates,
/// because everything between them is shaded and neither end is meaningful
/// without the other. So the mode and the dates travel together, and `contains`,
/// [#isStart], [#isEnd] and [#covers] are four different questions rather than
/// one asked four times.
///
/// ## Order is the user's, not the calendar's
///
/// [#dates] is kept in the order it was chosen for [Mode#MULTIPLE], because that
/// is what an application reporting a set back to a server wants and sorting it
/// would throw the information away. A [Mode#RANGE] is the exception: its two
/// entries are **ordered**, because a range dragged backwards is the same range
/// as one dragged forwards and an application should not have to normalise it.
///
/// @param mode  how many dates may be chosen, and what they mean together
/// @param dates what is chosen — empty, one, several, or a range's two ends
public record DateSelection(Mode mode, List<LocalDate> dates) {

    /// How many dates a calendar may hold, and what they mean together.
    public enum Mode {

        /// One date, and pressing another replaces it. The default, and what a
        /// `date-picker` uses.
        SINGLE,

        /// Several dates, each pressed to add and pressed again to remove.
        ///
        /// No `Ctrl` and no `Shift`: a list needs modifiers because a plain click
        /// there means "only this one", and a calendar of separate dates has no
        /// such meaning to preserve — every press is a toggle, which is what
        /// every date-picking interface that offers this does.
        MULTIPLE,

        /// Two dates and everything between them. `date-picker range=#true`.
        RANGE
    }

    /// Nothing chosen, in [Mode#SINGLE].
    public static final DateSelection NONE = new DateSelection(Mode.SINGLE, List.of());

    public DateSelection {
        Objects.requireNonNull(mode, "mode");
        dates = List.copyOf(dates == null ? List.<LocalDate>of() : dates);
        if (mode != Mode.MULTIPLE && dates.size() > 2) {
            throw new IllegalArgumentException(mode + " holds at most two dates, and " + dates.size() + " were given");
        }
        if (mode == Mode.SINGLE && dates.size() > 1) {
            throw new IllegalArgumentException("a single selection holds one date, and " + dates.size() + " were"
                    + " given; a calendar that showed two would be one whose mode and value disagree");
        }
        if (mode == Mode.RANGE && dates.size() == 2 && dates.get(1).isBefore(dates.get(0))) {
            dates = List.of(dates.get(1), dates.get(0));
        }
    }

    /// Nothing chosen, in `mode`.
    public static DateSelection empty(Mode mode) {
        return new DateSelection(mode, List.of());
    }

    /// One date, chosen.
    public static DateSelection of(LocalDate date) {
        return new DateSelection(Mode.SINGLE, List.of(Objects.requireNonNull(date, "date")));
    }

    /// A range with both ends, or only its start when `end` is null.
    public static DateSelection range(LocalDate start, @Nullable LocalDate end) {
        Objects.requireNonNull(start, "start");
        return new DateSelection(Mode.RANGE, end == null ? List.of(start) : List.of(start, end));
    }

    /// Whether nothing is chosen.
    public boolean isEmpty() {
        return dates.isEmpty();
    }

    /// The one date, or the range's start, or null.
    ///
    /// What a `date-picker` reads to fill its field, and what an application with
    /// a single-date calendar wants without unpacking a list.
    public @Nullable LocalDate first() {
        return dates.isEmpty() ? null : dates.getFirst();
    }

    /// The range's end, or null — including for a range that has only been
    /// half chosen, which is the state between the two presses.
    public @Nullable LocalDate last() {
        return mode == Mode.RANGE && dates.size() == 2 ? dates.get(1) : null;
    }

    /// Whether `date` is one of the chosen ones.
    ///
    /// **Not** whether it is inside a range — that is [#covers], and the two are
    /// deliberately separate because §2 asks for a `full` radius on "the selected
    /// day, range ends only". A day in the middle of a range is shaded and is not
    /// a chosen date.
    public boolean contains(LocalDate date) {
        return dates.contains(date);
    }

    /// Whether `date` is the start of a range.
    public boolean isStart(LocalDate date) {
        return mode == Mode.RANGE && !dates.isEmpty() && dates.getFirst().equals(date);
    }

    /// Whether `date` is the end of a range.
    public boolean isEnd(LocalDate date) {
        return mode == Mode.RANGE && dates.size() == 2 && dates.get(1).equals(date);
    }

    /// Whether `date` falls **strictly inside** a complete range.
    ///
    /// Strictly, so that the two ends are `isStart` and `isEnd` and nothing else,
    /// which is what lets the stylesheet round the ends and leave the middle
    /// square without a rule having to say "unless".
    public boolean covers(LocalDate date) {
        var end = last();
        return end != null && date.isAfter(dates.getFirst()) && date.isBefore(end);
    }

    /// This selection after a press on `date` — the whole of what clicking a day
    /// does.
    ///
    /// - **Single**: `date` replaces whatever was there. Pressing the chosen day
    ///   again keeps it rather than clearing: a single-date calendar with nothing
    ///   chosen is a state a user reaches by accident and cannot see, and a
    ///   `date-picker` would empty its own field on a second click.
    /// - **Multiple**: `date` is added, or removed if it was already there.
    /// - **Range**: the first press starts a range and drops whatever was there,
    ///   the second completes it, and a third starts again. Completing it
    ///   *before* the start is not an error — the constructor orders the pair,
    ///   because a range dragged backwards is the same range.
    public DateSelection with(LocalDate date) {
        Objects.requireNonNull(date, "date");
        return switch (mode) {
            case SINGLE -> new DateSelection(mode, List.of(date));
            case MULTIPLE -> {
                var next = new ArrayList<>(dates);
                if (!next.remove(date)) {
                    next.add(date);
                }
                yield new DateSelection(mode, next);
            }
            case RANGE ->
                dates.size() == 1
                        ? new DateSelection(mode, List.of(dates.getFirst(), date))
                        : new DateSelection(mode, List.of(date));
        };
    }

    /// This selection with nothing in it.
    public DateSelection cleared() {
        return dates.isEmpty() ? this : new DateSelection(mode, List.of());
    }

    /// This selection read as `mode` — what a calendar does when its widget is
    /// rebuilt with a different one.
    ///
    /// Anything that does not fit is dropped rather than reshaped: a set of five
    /// dates read as a range would have to invent which two were meant.
    public DateSelection as(Mode wanted) {
        if (wanted == mode) {
            return this;
        }
        return switch (wanted) {
            case SINGLE -> dates.isEmpty() ? empty(wanted) : new DateSelection(wanted, List.of(dates.getFirst()));
            case MULTIPLE -> new DateSelection(wanted, dates);
            case RANGE -> new DateSelection(wanted, dates.size() > 2 ? dates.subList(0, 2) : dates);
        };
    }
}
