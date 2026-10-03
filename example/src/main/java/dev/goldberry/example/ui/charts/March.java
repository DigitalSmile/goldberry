package dev.goldberry.example.ui.charts;

import java.time.Instant;
import java.util.Arrays;
import java.util.List;

/// The numbers every chart on the screen draws: a journal of one march.
///
/// Written down rather than generated, because a screen of the gallery is also a
/// golden image, and data from a clock or a random source would make a picture
/// that cannot be compared with yesterday's. A journal rather than `Series 1`
/// and `Series 2`, because a chart of placeholders shows whether a legend draws
/// and not whether it reads.
///
/// Read more: [Charts](https://goldberry.dev/docs/components/charts.html#what-the-five-share).
final class March {

    /// The seven days most charts here are sampled on.
    static final List<String> DAYS = List.of("Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun");

    /// Leagues covered each day, which the shared-crosshair card draws as a line.
    static final List<Double> LEAGUES = List.of(12.0, 19.0, 15.0, 27.0, 31.0, 28.0, 36.0);

    /// Hours of rest each day, the same seven days, drawn as bars beside the line.
    static final List<Double> RESTS = List.of(9.0, 7.0, 8.0, 5.0, 4.0, 6.0, 3.0);

    /// The packs, day by day: the two bands of the stacked area chart.
    static final List<Double> LEMBAS = List.of(40.0, 52.0, 44.0, 61.0, 58.0, 66.0, 71.0);

    static final List<Double> DRIED_MEAT = List.of(12.0, 9.0, 15.0, 11.0, 14.0, 10.0, 13.0);

    /// Days without loss, for the rising sparkline.
    static final List<Double> SAFE = List.of(61.0, 68.0, 74.0, 79.0, 83.0, 86.0, 89.0, 91.0, 93.0);

    /// Leagues still to go, for the falling one.
    static final List<Double> REMAINING = List.of(1520.0, 1487.0, 1442.0, 1409.0, 1381.0, 1358.0, 1340.0);

    /// Minutes each watch was kept, read every half hour.
    static final List<Double> WATCH = List.of(128.0, 131.0, 126.0, 149.0, 142.0, 138.0, 133.0, 129.0, 124.0, 121.0);

    /// When each of [#WATCH]'s readings was taken: every half hour, with one missed
    /// turn between the eighth and the ninth, so the time axis is three hours wide
    /// there rather than one step.
    ///
    /// In UTC, which an application would not do. A picture that changed with the
    /// machine's zone could not be compared with the one before it.
    static final List<Instant> NIGHTS = List.of(
            Instant.parse("2026-03-14T21:00:00Z"),
            Instant.parse("2026-03-14T21:30:00Z"),
            Instant.parse("2026-03-14T22:00:00Z"),
            Instant.parse("2026-03-14T22:30:00Z"),
            Instant.parse("2026-03-14T23:00:00Z"),
            Instant.parse("2026-03-14T23:30:00Z"),
            Instant.parse("2026-03-15T00:00:00Z"),
            Instant.parse("2026-03-15T00:30:00Z"),
            Instant.parse("2026-03-15T03:30:00Z"),
            Instant.parse("2026-03-15T04:00:00Z"));

    /// Twelve readings with three missing, arranged so both shapes a hole makes
    /// are on screen: a two-reading gap between two segments, and a lone reading
    /// between two holes, which is drawn as a dot.
    ///
    /// `NaN` is how a hole is spelled, so this is a list that takes it.
    static final List<Double> BEACONS =
            Arrays.asList(7.0, 9.0, 8.0, Double.NaN, Double.NaN, 11.0, Double.NaN, 10.0, 12.0, 14.0, 15.0, 16.0);

    private March() {}
}
