package dev.goldberry.widgets.form.timepicker;

import java.time.LocalTime;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.Predicate;

import org.jspecify.annotations.Nullable;

import dev.goldberry.widget.State;
import dev.goldberry.widget.Widget;

/// The hour/minute/second wheels a [TimePicker] opens — its column set.
///
/// Package-private and not a widget an application names, unlike `calendar`.
/// That asymmetry is deliberate: a calendar is a widget of its own, one somebody
/// would put on a page, and a time's columns are no widget at
/// all — they exist only inside a picker's popover, and a bare column set on a
/// form would be three spinners in a trench coat.
///
/// @param value      what the wheels currently show, or null for nothing chosen
/// @param fallback   what to show when `value` is null — where the wheels start
/// @param precision  how many columns there are
/// @param allows     which times the picker will accept, asked before reporting
/// @param onCommit   told a time when one is chosen
record TimeColumns(
        @Nullable LocalTime value,
        LocalTime fallback,
        TimePrecision precision,
        Predicate<LocalTime> allows,
        Consumer<LocalTime> onCommit)
        implements Widget.Stateful {

    /// How many rows a wheel shows. Odd, so that the current value is the middle
    /// one — an even count has no middle and would put the value off-centre by
    /// half a row, which reads as a list that has not finished scrolling.
    static final int VISIBLE_ROWS = 5;

    TimeColumns {
        Objects.requireNonNull(fallback, "fallback");
        Objects.requireNonNull(precision, "precision");
        Objects.requireNonNull(allows, "allows");
        Objects.requireNonNull(onCommit, "onCommit");
    }

    /// What the wheels start from: the value when there is one, and the picker's
    /// fallback when there is not.
    ///
    /// Truncated to the precision, so a picker showing hours and minutes cannot
    /// open on a time with seconds in it that no column can express or change.
    LocalTime shown() {
        return precision.truncate(value == null ? fallback : value);
    }

    @Override
    public State<?> createState() {
        return new TimeColumnsState();
    }
}
