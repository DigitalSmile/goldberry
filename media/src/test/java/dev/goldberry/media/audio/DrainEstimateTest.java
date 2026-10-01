package dev.goldberry.media.audio;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/// [DrainEstimate] with the time in the test's hands (ADR-0485): a device that
/// pulls 1024 samples at 48 kHz, every 21.33 ms when it is punctual, and early,
/// late, twice at once or not at all when it is not. The estimate follows it
/// without a step.
@DisplayName("DrainEstimate")
class DrainEstimateTest {

    private static final int RATE = 48_000;
    private static final int PULL = 1024;
    /// A pull's length in nanoseconds: 21.33 ms.
    private static final long PULL_NANOS = PULL * 1_000_000_000L / RATE;
    private static final long MS = 1_000_000L;

    /// Samples played in `nanos` at the stream's rate.
    private static double samples(long nanos) {
        return nanos * RATE / 1e9;
    }

    @Test
    @DisplayName("follows punctual pulls exactly: a line at the stream's rate")
    void punctual() {
        var drain = new DrainEstimate(RATE, 0);
        var taken = 0L;
        for (var pull = 0; pull < 20; pull++) {
            var at = pull * PULL_NANOS;
            drain.pulled(PULL, at);
            taken += PULL;
            for (var step = 0; step < 4; step++) {
                var now = at + step * PULL_NANOS / 4;
                // Played is what was taken before this pull, and what of this
                // pull has drained: the line through each pull.
                var played = taken - PULL + drain.drained(now);
                assertEquals(samples(now), played, 1.5, "at " + now / 1e6 + " ms");
            }
        }
        assertEquals(PULL, drain.typicalPull());
    }

    /// The largest step the estimate takes between readings `interval` apart
    /// while the device pulls at `pullTimes`, in samples beyond what the
    /// stream's own rate would move it.
    private static double largestExcess(long[] pullTimes, long interval, long until) {
        var drain = new DrainEstimate(RATE, 0);
        var next = 0;
        var taken = 0L;
        var last = Double.NaN;
        var worst = 0.0;
        for (var now = 0L; now <= until; now += interval) {
            while (next < pullTimes.length && pullTimes[next] <= now) {
                drain.pulled(PULL, now);
                taken += PULL;
                next++;
            }
            var played = taken + drain.drained(now);
            // From the first reading on: the first pull is where the line starts.
            if (!Double.isNaN(last)) {
                assertTrue(played >= last - 1e-6, "the estimate went back at " + now / 1e6 + " ms");
                worst = Math.max(worst, played - last - samples(interval));
            }
            last = played;
        }
        return worst;
    }

    @Test
    @DisplayName("an early pull, and two at once, are caught up by a tenth faster at most, never in a step")
    void earlyPullsAreSlewed() {
        // Punctual, then one 12 ms early, then two 1 ms apart, then punctual again.
        var times = new long[40];
        for (var i = 0; i < times.length; i++) {
            times[i] = i * PULL_NANOS;
        }
        times[10] -= 12 * MS;
        times[20] = times[19] + MS;
        var excess = largestExcess(times, 2 * MS, 40 * PULL_NANOS);
        // A reading 2 ms after the last moves by 2 ms of audio and a tenth of it
        // more at the most: 9.6 samples.
        assertTrue(
                excess <= samples(2 * MS) * DrainEstimate.MAX_SLEW + 0.5,
                "a step of " + excess + " samples past the rate");
    }

    @Test
    @DisplayName("a late pull stalls the estimate at a pull past what was taken, and it never goes back")
    void latePullStalls() {
        var drain = new DrainEstimate(RATE, 0);
        drain.pulled(PULL, 0);
        // 40 ms with no pull: the estimate plays the pull out and waits there.
        assertEquals(PULL, drain.drained(40 * MS));
        assertEquals(PULL, drain.drained(50 * MS));
        drain.pulled(PULL, 55 * MS);
        // The pull that came late starts where the last one ended, with no step back.
        assertEquals(0, drain.drained(55 * MS));
    }

    @Test
    @DisplayName("pulls that went unseen go straight to the measurement, and a doubled pull is not the typical one")
    void unseenPullsSnap() {
        var drain = new DrainEstimate(RATE, 0);
        for (var i = 0; i < 10; i++) {
            drain.pulled(PULL, i * PULL_NANOS);
        }
        // Nobody asked for 250 ms, and the device took twelve pulls meanwhile.
        var at = 9 * PULL_NANOS + 250 * MS;
        drain.pulled(12L * PULL, at);
        assertEquals(0, drain.drained(at), "far behind, it snaps rather than slews");
        assertEquals(PULL, drain.typicalPull());
        drain.pulled(2L * PULL, at + PULL_NANOS);
        assertEquals(PULL, drain.typicalPull(), "two pulls seen at once are not the device's pull");
    }

    @Test
    @DisplayName("stands still while paused, resumes where it stood, and starts over at a reset")
    void pauseAndReset() {
        var drain = new DrainEstimate(RATE, 0);
        drain.pulled(PULL, 0);
        var before = drain.drained(10 * MS);
        drain.pause(10 * MS);
        assertEquals(before, drain.drained(500 * MS), "paused, nothing drains");
        drain.resume(500 * MS);
        assertEquals(before + Math.round(samples(5 * MS)), drain.drained(505 * MS), 1);
        drain.reset(600 * MS);
        assertEquals(0, drain.drained(610 * MS), "nothing taken since the reset");
        assertEquals(0, drain.typicalPull());
    }

    @Test
    @DisplayName("at twice the rate drains twice as fast, keeping what it had drained")
    void rate() {
        var drain = new DrainEstimate(RATE, 0);
        drain.pulled(PULL, 0);
        var half = drain.drained(PULL_NANOS / 2);
        drain.rate(2, PULL_NANOS / 2);
        assertEquals(half, drain.drained(PULL_NANOS / 2));
        assertEquals(PULL, drain.drained(PULL_NANOS / 2 + PULL_NANOS / 4), 2);
        assertThrows(IllegalArgumentException.class, () -> drain.rate(0, 0));
        assertThrows(IllegalArgumentException.class, () -> new DrainEstimate(0, 0));
    }
}
