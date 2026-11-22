package dev.goldberry.example.capture;

import java.time.Duration;
import java.util.ArrayDeque;
import java.util.Objects;
import java.util.function.LongSupplier;

import dev.goldberry.motion.Clock;

/// How often something arrives, and how much of it: blocks of samples, or
/// pictures and their bytes, over a sliding window of recent arrivals.
///
/// Recorded on whatever thread the arrivals are heard on, and read on the UI
/// thread. The clock is the caller's, so a test moves it by hand.
///
/// Read more: [Audio and video](https://goldberry.dev/docs/components/media.html).
public final class RateMeter {

    /// What the meter says about the arrivals so far.
    ///
    /// @param events          how many have arrived since the meter started
    /// @param total           the sum of their amounts since it started
    /// @param eventsPerSecond the rate over the window, or zero with fewer than
    ///                        two arrivals in it
    /// @param amountPerSecond the amount a second over the window, likewise
    /// @param meanInterval    the mean gap between arrivals in the window
    /// @param longestInterval the longest gap in the window
    public record Reading(
            long events,
            long total,
            double eventsPerSecond,
            double amountPerSecond,
            Duration meanInterval,
            Duration longestInterval) {

        /// Nothing has arrived.
        public static final Reading NONE = new Reading(0, 0, 0, 0, Duration.ZERO, Duration.ZERO);
    }

    private record Arrival(long at, long amount) {}

    private final long windowNanos;
    private final LongSupplier clock;
    private final ArrayDeque<Arrival> recent = new ArrayDeque<>();
    private long events;
    private long total;

    /// A meter averaging over `window`, reading time from `clock` in
    /// nanoseconds.
    public RateMeter(Duration window, LongSupplier clock) {
        if (window.isNegative() || window.isZero()) {
            throw new IllegalArgumentException("a window of " + window);
        }
        this.windowNanos = window.toNanos();
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    /// A meter over the last two seconds of `clock`: a window's own clock,
    /// which is the system's in a window and the test's in a test.
    public static RateMeter overTwoSecondsOf(Clock clock) {
        Objects.requireNonNull(clock, "clock");
        return new RateMeter(Duration.ofSeconds(2), () -> Math.round(clock.nowMillis() * 1e6));
    }

    /// Something arrived now, of `amount`: a block's frames, a picture's bytes.
    public synchronized void record(long amount) {
        var now = clock.getAsLong();
        events++;
        total += amount;
        recent.addLast(new Arrival(now, amount));
        while (recent.size() > 1 && now - recent.peekFirst().at() > windowNanos) {
            recent.removeFirst();
        }
    }

    /// What it says now.
    public synchronized Reading reading() {
        if (recent.size() < 2) {
            return new Reading(events, total, 0, 0, Duration.ZERO, Duration.ZERO);
        }
        var first = recent.peekFirst().at();
        var span = recent.peekLast().at() - first;
        var longest = 0L;
        var previous = first;
        var amount = 0L;
        var skipFirst = true;
        for (var arrival : recent) {
            longest = Math.max(longest, arrival.at() - previous);
            previous = arrival.at();
            if (skipFirst) {
                skipFirst = false;
            } else {
                amount += arrival.amount();
            }
        }
        var gaps = recent.size() - 1;
        var seconds = span / 1e9;
        return new Reading(
                events,
                total,
                span == 0 ? 0 : gaps / seconds,
                span == 0 ? 0 : amount / seconds,
                Duration.ofNanos(span / gaps),
                Duration.ofNanos(longest));
    }
}
