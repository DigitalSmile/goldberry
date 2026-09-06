package io.github.digitalsmile.goldberry.widgets.form.timepicker;

import java.time.LocalTime;
import java.time.temporal.ChronoUnit;

/// How far down a [TimePicker] goes — §4's "an hour/minute/second column set".
///
/// The specification names three columns and every clock in the world shows two,
/// so this is which of them a picker has rather than a fixed set. A field that
/// asks for a meeting time and offers seconds is asking a question nobody meant.
///
/// **Minutes are the default**, which is the reading of "hour/minute/second" that
/// leaves the common case right: the sentence lists what the column set is made
/// of, not what every picker must show.
public enum TimePrecision {

    /// Hours only, on the hour. A booking slot.
    HOURS(ChronoUnit.HOURS, 1),

    /// Hours and minutes — the default, and what a clock face has.
    MINUTES(ChronoUnit.MINUTES, 2),

    /// All three. A duration, a log timestamp, a stopwatch.
    SECONDS(ChronoUnit.SECONDS, 3);

    private final ChronoUnit unit;
    private final int columns;

    TimePrecision(ChronoUnit unit, int columns) {
        this.unit = unit;
        this.columns = columns;
    }

    /// How many columns a picker at this precision has.
    ///
    /// A field rather than `ordinal() + 1`, which is what it was first: an
    /// ordinal is a *declaration order*, and a constant added or reordered here
    /// would silently change how many wheels a picker draws. Error Prone refuses
    /// the arithmetic for exactly that reason and is right to.
    public int columns() {
        return columns;
    }

    /// Whether this precision has a minutes column.
    public boolean hasMinutes() {
        return this != HOURS;
    }

    /// Whether this precision has a seconds column.
    public boolean hasSeconds() {
        return this == SECONDS;
    }

    /// `time` with everything finer than this thrown away.
    ///
    /// What makes a picker's value match what its columns can express: a control
    /// showing hours and minutes must not report a time with seconds in it that
    /// nothing on screen said, which is how a form ends up rejecting a value the
    /// user watched themselves choose.
    public LocalTime truncate(LocalTime time) {
        return time.truncatedTo(unit);
    }
}
