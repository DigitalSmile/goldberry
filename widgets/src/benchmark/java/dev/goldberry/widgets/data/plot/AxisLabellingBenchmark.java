package dev.goldberry.widgets.data.plot;

import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import dev.goldberry.junit.TimeBudget;

/// What labelling an axis costs, numeric and in time.
///
/// An axis is labelled once per axis per frame, so the search behind it has to be
/// a small part of a frame: a millisecond would be a third of one at 60 Hz. The
/// tests hold the *answers* -- a span of centuries does not run away, a step is a
/// number a reader can do arithmetic with -- and cannot hold the cost, because a
/// clock is not a count: what this watches is the work a search does, and under a
/// parallel build it measures the machine's load as much as the search.
///
/// **A benchmark, so `check` compiles it and never runs it.** It prints what one
/// labelling costs and fails only past four times what it is written against,
/// widened by `-Dgoldberry.timing.slack` and never as far as the millisecond.
///
/// ```sh
/// ./gradlew :widgets:benchmark --tests '*AxisLabellingBenchmark*'
/// ```
@DisplayName("Axis labelling, per call")
class AxisLabellingBenchmark {

    /// What one labelling may take: twice what it was first held to, and well
    /// short of the millisecond that would be a third of a frame.
    private static final TimeBudget PER_CALL =
            TimeBudget.of(Duration.ofNanos(400_000)).shortOf(Duration.ofMillis(1));

    private static final int WARMUP = 200;
    private static final int RUNS = 1_000;

    @Test
    @DisplayName("a numeric axis answers quickly enough to run inside a frame")
    void numeric() {
        for (var i = 0; i < WARMUP; i++) {
            Ticks.extended(0, 97 + i, 5);
        }
        var started = System.nanoTime();
        for (var i = 0; i < RUNS; i++) {
            Ticks.extended(0, 97 + i, 5);
        }
        report("Ticks.extended", started);
    }

    @Test
    @DisplayName("a time axis answers quickly enough to run once per axis per frame")
    void time() {
        var from = Instant.parse("2026-01-01T00:00:00Z");
        var to = Instant.parse("2026-12-31T00:00:00Z");
        for (var i = 0; i < WARMUP; i++) {
            TimeTicks.of(from, to, 6, ZoneOffset.UTC);
        }
        var started = System.nanoTime();
        for (var i = 0; i < RUNS; i++) {
            TimeTicks.of(from, to, 6, ZoneOffset.UTC);
        }
        report("TimeTicks.of", started);
    }

    private static void report(String what, long startedNanos) {
        var each = Duration.ofNanos((System.nanoTime() - startedNanos) / RUNS);
        System.out.printf(
                "%n  %-16s %8.1f µs per call (allowed %.1f)%n",
                what, each.toNanos() / 1e3, PER_CALL.allowed().toNanos() / 1e3);
        PER_CALL.assertWithin(each, what + ", one labelling");
    }
}
