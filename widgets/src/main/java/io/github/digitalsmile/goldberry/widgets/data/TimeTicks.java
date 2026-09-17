package io.github.digitalsmile.goldberry.widgets.data;

import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/// Where the labels go on a time axis — `content-widgets.md` §3.1's "tick
/// stepping across sec/min/hour/day/month/year boundaries".
///
/// [Ticks] is Wilkinson's algorithm for *numbers*, and it is the wrong shape
/// here for one reason: a nice number is a round multiple, and time has no round
/// multiples. Sixty seconds are a minute, sixty minutes an hour, twenty-four
/// hours a day — and then a month is 28, 29, 30 or 31 days and a year is 365 or
/// 366. A step of "2 592 000 000 ms" is a month only in a year with no February
/// in it, and a step of "86 400 000 ms" is a day except on the two days a year a
/// zone changes offset, when it is 23 or 25 hours.
///
/// So this steps in **`java.time`**, which knows all of that, and the only
/// arithmetic done on numbers here is choosing which rung of the ladder to
/// stand on.
///
/// ## The ladder
///
/// The steps people actually read a clock in: 1, 2, 5, 10, 15, 30 seconds; the
/// same minutes; 1, 2, 3, 6, 12 hours; 1, 2, 7, 14 days; 1, 3, 6 months; 1, 2, 5,
/// 10 years and up. There is no 7-second or 4-hour step, because nobody divides a
/// minute into sevenths or a day into sixths — the rungs are the ones a reader
/// can do arithmetic against without being told what they are.
///
/// ## Snapping
///
/// A tick lands on a **boundary of its own unit**: 15-minute ticks at `:00`,
/// `:15`, `:30`, `:45` and not at `:07`, `:22`; a monthly tick on the first of
/// the month; a yearly tick on the first of January. An axis whose labels are
/// round times you would say out loud is the whole point of the exercise, and it
/// is what a step measured from the first data point could never give.
public final class TimeTicks {

    private TimeTicks() {}

    /// One rung: an amount of a unit, and how long that is in milliseconds when
    /// you only need to know *roughly*.
    ///
    /// The approximation is for choosing the rung and nothing else. A month is
    /// 30.44 days here and exactly as long as it is when the ticks are actually
    /// produced.
    private record Step(ChronoUnit unit, int amount, double approxMillis) {}

    private static final double SECOND = 1000;
    private static final double MINUTE = 60 * SECOND;
    private static final double HOUR = 60 * MINUTE;
    private static final double DAY = 24 * HOUR;

    private static final List<Step> LADDER = List.of(
            new Step(ChronoUnit.SECONDS, 1, SECOND),
            new Step(ChronoUnit.SECONDS, 2, 2 * SECOND),
            new Step(ChronoUnit.SECONDS, 5, 5 * SECOND),
            new Step(ChronoUnit.SECONDS, 10, 10 * SECOND),
            new Step(ChronoUnit.SECONDS, 15, 15 * SECOND),
            new Step(ChronoUnit.SECONDS, 30, 30 * SECOND),
            new Step(ChronoUnit.MINUTES, 1, MINUTE),
            new Step(ChronoUnit.MINUTES, 2, 2 * MINUTE),
            new Step(ChronoUnit.MINUTES, 5, 5 * MINUTE),
            new Step(ChronoUnit.MINUTES, 10, 10 * MINUTE),
            new Step(ChronoUnit.MINUTES, 15, 15 * MINUTE),
            new Step(ChronoUnit.MINUTES, 30, 30 * MINUTE),
            new Step(ChronoUnit.HOURS, 1, HOUR),
            new Step(ChronoUnit.HOURS, 2, 2 * HOUR),
            new Step(ChronoUnit.HOURS, 3, 3 * HOUR),
            new Step(ChronoUnit.HOURS, 6, 6 * HOUR),
            new Step(ChronoUnit.HOURS, 12, 12 * HOUR),
            new Step(ChronoUnit.DAYS, 1, DAY),
            new Step(ChronoUnit.DAYS, 2, 2 * DAY),
            new Step(ChronoUnit.DAYS, 7, 7 * DAY),
            new Step(ChronoUnit.DAYS, 14, 14 * DAY),
            new Step(ChronoUnit.MONTHS, 1, 30.44 * DAY),
            new Step(ChronoUnit.MONTHS, 3, 91.31 * DAY),
            new Step(ChronoUnit.MONTHS, 6, 182.62 * DAY),
            new Step(ChronoUnit.YEARS, 1, 365.25 * DAY),
            new Step(ChronoUnit.YEARS, 2, 730.5 * DAY),
            new Step(ChronoUnit.YEARS, 5, 1826.25 * DAY),
            new Step(ChronoUnit.YEARS, 10, 3652.5 * DAY));

    /// What a tick is written as, chosen by how far apart the ticks are.
    ///
    /// **The root locale**, which is the rule every other label in this toolkit
    /// follows: a golden image formatted in the machine's language is a test that
    /// passes in one country. The *zone* is the application's — see [TimeAxis].
    ///
    /// The pattern comes from the step rather than from the value, for the reason
    /// a numeric axis takes its decimals from the step: a column where one label
    /// says `14:00` and the next says `12 Mar` is a column that reads as ragged.
    private static DateTimeFormatter formatFor(Step step) {
        var pattern =
                switch (step.unit()) {
                    case SECONDS -> "HH:mm:ss";
                    case MINUTES, HOURS -> "HH:mm";
                    case DAYS -> "d MMM";
                    case MONTHS -> "MMM yyyy";
                    default -> "yyyy";
                };
        return DateTimeFormatter.ofPattern(pattern, Locale.ROOT);
    }

    /// How many labels `step` would put on a span of `span` milliseconds.
    ///
    /// Rounded up, because a rung that fits four and a half times is read five
    /// times, and saturated at an int rather than left a double: the only
    /// question ever asked of it is "more than wanted?", and a span of four
    /// centuries at one-second rungs answers that at any width.
    private static int labels(double span, Step step) {
        return (int) Math.min(Integer.MAX_VALUE, Math.ceil(span / step.approxMillis()));
    }

    /// The labelling of `from…to` at about `target` labels.
    ///
    /// @param from   the first instant on the axis
    /// @param to     the last
    /// @param target how many labels to aim for — a preference, exactly as
    ///               [Ticks#extended]'s is
    /// @param zone   which day is which
    public static Labelling of(Instant from, Instant to, int target, ZoneId zone) {
        java.util.Objects.requireNonNull(from, "from");
        java.util.Objects.requireNonNull(to, "to");
        java.util.Objects.requireNonNull(zone, "zone");
        var wanted = Math.max(2, target);
        var span = (double) (to.toEpochMilli() - from.toEpochMilli());
        if (!(span > 0)) {
            // One instant, or a range that goes backwards. One label, which is
            // the honest picture of a series that happened at a moment.
            var step = LADDER.getFirst();
            return new Labelling(List.of(from), formatFor(step), step.unit(), step.amount());
        }

        // The first rung that does not produce more labels than were asked for.
        // Smallest-first, so a range that fits a fine step gets one: a ten-minute
        // window labelled in hours has two labels and says nothing.
        var chosen = LADDER.getLast();
        for (var step : LADDER) {
            if (labels(span, step) <= wanted) {
                chosen = step;
                break;
            }
        }
        // Past the top of the ladder, the rung is decades of whatever it takes.
        // A chart of four centuries is not a case worth a special algorithm, and
        // one worth refusing even less.
        while (labels(span, chosen) > wanted && chosen.unit() == ChronoUnit.YEARS) {
            chosen = new Step(ChronoUnit.YEARS, chosen.amount() * 2, chosen.approxMillis() * 2);
        }

        var values = new ArrayList<Instant>();
        var at = snap(from.atZone(zone), chosen);
        if (at.toInstant().isBefore(from)) {
            at = at.plus(chosen.amount(), chosen.unit());
        }
        // **Stepped in java.time**, so a month is as long as that month is and a
        // day across a zone change is 23 or 25 hours. Adding a fixed number of
        // milliseconds is the version of this that drifts an hour twice a year
        // and a day every February.
        while (!at.toInstant().isAfter(to)) {
            values.add(at.toInstant());
            var next = at.plus(chosen.amount(), chosen.unit());
            if (!next.isAfter(at)) {
                // Cannot happen with a positive amount, and a guard rather than a
                // comment because the loop is the one place a bad rung would hang
                // the paint thread rather than draw something wrong.
                break;
            }
            at = next;
        }
        return new Labelling(List.copyOf(values), formatFor(chosen), chosen.unit(), chosen.amount());
    }

    /// `time` moved back to the previous boundary of `step`'s own unit.
    private static ZonedDateTime snap(ZonedDateTime time, Step step) {
        return switch (step.unit()) {
            case SECONDS -> time.truncatedTo(ChronoUnit.MINUTES).plusSeconds(floor(time.getSecond(), step.amount()));
            case MINUTES -> time.truncatedTo(ChronoUnit.HOURS).plusMinutes(floor(time.getMinute(), step.amount()));
            case HOURS -> time.truncatedTo(ChronoUnit.DAYS).plusHours(floor(time.getHour(), step.amount()));
            // A day step snaps to midnight and no further: a "week" boundary is
            // Sunday in one country and Monday in the next, and a 14-day step has
            // no natural boundary at all. Midnight is a boundary every reader
            // agrees about.
            case DAYS -> time.truncatedTo(ChronoUnit.DAYS);
            case MONTHS ->
                time.withDayOfMonth(1)
                        .truncatedTo(ChronoUnit.DAYS)
                        .withMonth(1 + floor(time.getMonthValue() - 1, step.amount()));
            default ->
                time.withDayOfYear(1).truncatedTo(ChronoUnit.DAYS).withYear(floor(time.getYear(), step.amount()));
        };
    }

    /// `value` rounded down to a multiple of `amount`.
    private static int floor(int value, int amount) {
        return Math.floorDiv(value, amount) * amount;
    }

    /// Where the labels go, how they are written, and how far apart they are.
    ///
    /// @param values the tick instants, in order — possibly empty for a span
    ///               shorter than one step
    /// @param format what to write each one as, in the root locale
    /// @param unit   the unit the step is counted in
    /// @param amount how many of them
    public record Labelling(List<Instant> values, DateTimeFormatter format, ChronoUnit unit, int amount) {

        /// `at` as its label, in `zone`.
        public String label(Instant at, ZoneId zone) {
            return format.format(at.atZone(zone));
        }
    }
}
