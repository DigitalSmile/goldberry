package io.github.digitalsmile.goldberry.widgets.data;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/// Where the labels go on a time axis.
///
/// The interesting cases are the ones where time is **not** a number of
/// milliseconds: a month is 28 to 31 days, a year is 365 or 366, and a day is 23
/// or 25 hours twice a year in most of the world. Every one of those is a step
/// somebody would otherwise write as a constant.
class TimeTicksTest {

    private static final ZoneId UTC = ZoneOffset.UTC;

    /// The labels of `from…to`, as strings, which is what a reader sees.
    private static List<String> labels(Instant from, Instant to, int target, ZoneId zone) {
        var labelling = TimeTicks.of(from, to, target, zone);
        return labelling.values().stream().map(at -> labelling.label(at, zone)).toList();
    }

    private static Instant utc(String text) {
        return Instant.parse(text);
    }

    @Test
    @DisplayName("a ten-minute window is labelled in minutes, on the minute")
    void minutes() {
        var labels = labels(utc("2026-03-14T09:03:20Z"), utc("2026-03-14T09:13:20Z"), 5, UTC);

        // Ten minutes at five labels is a two-minute step, and a two-minute
        // boundary is an **even** minute counted from the hour. So the first
        // label is `09:04` and not `09:03`, which is where the data starts, and
        // not `09:05`, which is a boundary of a step nobody chose.
        assertEquals(List.of("09:04", "09:06", "09:08", "09:10", "09:12"), labels);
    }

    /// The rung is chosen by how many labels it would produce against how many
    /// were asked for, and the boundary is inclusive: a span that fits a rung
    /// exactly takes it, and a span a second longer takes the next one up.
    @Test
    @DisplayName("a span that fits a rung exactly takes that rung")
    void exactFitTakesTheRung() {
        var labels = labels(utc("2026-03-14T09:00:00Z"), utc("2026-03-14T09:05:00Z"), 5, UTC);

        assertEquals(List.of("09:00", "09:01", "09:02", "09:03", "09:04", "09:05"), labels);
    }

    @Test
    @DisplayName("a span a second over the rung takes the next one up")
    void aHairOverTakesTheNextRung() {
        var labels = labels(utc("2026-03-14T09:00:00Z"), utc("2026-03-14T09:05:01Z"), 5, UTC);

        assertEquals(List.of("09:00", "09:02", "09:04"), labels);
    }

    @Test
    @DisplayName("a day is labelled in hours, and the hours are round")
    void hours() {
        var labels = labels(utc("2026-03-14T00:00:00Z"), utc("2026-03-15T00:00:00Z"), 5, UTC);

        assertEquals(List.of("00:00", "06:00", "12:00", "18:00", "00:00"), labels);
    }

    @Test
    @DisplayName("a quarter is labelled in months, on the first of the month")
    void months() {
        var labels = labels(utc("2026-01-17T00:00:00Z"), utc("2026-04-02T00:00:00Z"), 4, UTC);

        assertEquals(List.of("Feb 2026", "Mar 2026", "Apr 2026"), labels);
    }

    @Test
    @DisplayName("a month step is as long as the month it steps over")
    void februaryIsNotThirtyDays() {
        // The reason this is `java.time` and not arithmetic on milliseconds: a
        // step of 2 592 000 000 ms is a month only in a year with no February in
        // it, and by December it has drifted five days.
        var labelling = TimeTicks.of(utc("2026-01-01T00:00:00Z"), utc("2026-12-31T00:00:00Z"), 12, UTC);

        for (var value : labelling.values()) {
            assertEquals(1, value.atZone(UTC).getDayOfMonth(), value + " is not the first of a month");
            assertEquals(0, value.atZone(UTC).getHour(), value + " is not midnight");
        }
        assertTrue(labelling.values().size() >= 10, "a year has months in it");
    }

    @Test
    @DisplayName("a day step across a spring-forward is 23 hours, and lands on midnight")
    void daylightSavingIsNotADay() {
        // Europe/Berlin loses an hour on 2026-03-29. A step of 86 400 000 ms
        // would put every label after it at 01:00, then 02:00 after the autumn
        // change -- an axis that drifts an hour twice a year.
        var berlin = ZoneId.of("Europe/Berlin");
        // Five days at six labels, so the step is one day and every tick is a
        // local midnight -- one of which is on the far side of the change.
        var labelling = TimeTicks.of(utc("2026-03-27T00:00:00Z"), utc("2026-04-01T00:00:00Z"), 6, berlin);

        for (var value : labelling.values()) {
            var local = value.atZone(berlin);
            assertEquals(0, local.getHour(), value + " is not local midnight in Berlin");
            assertEquals(0, local.getMinute(), value + " is not on the hour");
        }
        // And the step really did cross the change: two consecutive ticks that
        // are 23 hours apart in real time and one day apart on the clock.
        var shortest = Long.MAX_VALUE;
        for (var i = 1; i < labelling.values().size(); i++) {
            shortest = Math.min(
                    shortest,
                    Duration.between(
                                    labelling.values().get(i - 1),
                                    labelling.values().get(i))
                            .toHours());
        }
        assertEquals(23, shortest, "the spring-forward day is 23 hours long");
    }

    @Test
    @DisplayName("the zone decides which day it is, and which days are on the axis at all")
    void theZoneIsNotDecoration() {
        // Chatham is UTC+13:45, so the moment this range starts is already a
        // quarter to two in the afternoon of the 14th there — and the first local
        // midnight inside the range is the 15th. The same four days of data have
        // five day-labels in one zone and four in the other, and both are right.
        var chatham = ZoneId.of("Pacific/Chatham");
        var from = utc("2026-03-14T00:00:00Z");
        var to = utc("2026-03-18T00:00:00Z");

        assertEquals(List.of("14 Mar", "15 Mar", "16 Mar", "17 Mar", "18 Mar"), labels(from, to, 4, UTC));
        assertEquals(
                List.of("15 Mar", "16 Mar", "17 Mar", "18 Mar"),
                labels(from, to, 4, chatham),
                "the 14th's midnight in Chatham is before this range begins");

        // And every tick is that zone's own midnight rather than UTC's.
        for (var value : TimeTicks.of(from, to, 4, chatham).values()) {
            var local = value.atZone(chatham);
            assertEquals(0, local.getHour(), value + " is not midnight in Chatham");
            assertEquals(0, local.getMinute(), value + " is not on a quarter hour");
        }
    }

    @Test
    @DisplayName("the ladder has the rungs a clock is read in and no others")
    void noSevenSecondSteps() {
        // Nobody divides a minute into sevenths. Every span this walks must land
        // on a step a reader can do arithmetic against.
        var allowed = java.util.Set.of(1, 2, 3, 5, 6, 7, 10, 12, 14, 15, 30);
        var from = utc("2026-03-14T00:00:00Z");
        for (var seconds : List.of(3L, 20L, 90L, 600L, 3600L, 40000L, 500000L)) {
            var labelling = TimeTicks.of(from, from.plusSeconds(seconds), 5, UTC);
            assertTrue(
                    allowed.contains(labelling.amount()),
                    "a step of " + labelling.amount() + " " + labelling.unit() + " for a span of " + seconds + "s");
        }
    }

    @Test
    @DisplayName("a span at every magnitude gets a step from the right unit")
    void everyMagnitude() {
        var from = utc("2020-01-01T00:00:00Z");

        assertEquals(
                ChronoUnit.SECONDS,
                TimeTicks.of(from, from.plusSeconds(20), 5, UTC).unit());
        assertEquals(
                ChronoUnit.MINUTES,
                TimeTicks.of(from, from.plusSeconds(600), 5, UTC).unit());
        assertEquals(
                ChronoUnit.HOURS,
                TimeTicks.of(from, from.plus(1, ChronoUnit.DAYS), 5, UTC).unit());
        assertEquals(
                ChronoUnit.DAYS,
                TimeTicks.of(from, from.plus(7, ChronoUnit.DAYS), 5, UTC).unit());
        assertEquals(
                ChronoUnit.MONTHS,
                TimeTicks.of(from, from.plus(200, ChronoUnit.DAYS), 5, UTC).unit());
        assertEquals(
                ChronoUnit.YEARS,
                TimeTicks.of(from, from.plus(4000, ChronoUnit.DAYS), 5, UTC).unit());
    }

    @Test
    @DisplayName("a range of one instant is one label rather than none")
    void aMomentIsAPicture() {
        var at = utc("2026-03-14T09:03:20Z");

        assertEquals(1, TimeTicks.of(at, at, 5, UTC).values().size());
    }

    @Test
    @DisplayName("it does not run away on a span of centuries")
    void theTopOfTheLadder() {
        var labelling = TimeTicks.of(utc("1600-01-01T00:00:00Z"), utc("2400-01-01T00:00:00Z"), 5, UTC);

        assertEquals(ChronoUnit.YEARS, labelling.unit());
        assertTrue(
                labelling.values().size() <= 8,
                "expected about five labels, got " + labelling.values().size());
    }

    @Test
    @DisplayName("it is fast enough to run once per axis per frame")
    void aMillisecondWouldBeAThirdOfAFrame() {
        var from = utc("2026-01-01T00:00:00Z");
        var to = utc("2026-12-31T00:00:00Z");
        for (var i = 0; i < 200; i++) {
            TimeTicks.of(from, to, 6, UTC);
        }

        var started = System.nanoTime();
        for (var i = 0; i < 1000; i++) {
            TimeTicks.of(from, to, 6, UTC);
        }
        var each = (System.nanoTime() - started) / 1000.0;

        // The same bound `Ticks` is held to, and for the same reason: this runs
        // per axis per frame, and a millisecond would be a third of a frame's
        // budget.
        assertTrue(each < 200_000, "a labelling took " + (each / 1000) + " µs");
    }
}
