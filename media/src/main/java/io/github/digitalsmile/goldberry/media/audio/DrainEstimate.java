package io.github.digitalsmile.goldberry.media.audio;

/// How far a device that takes samples in pulls has got through the samples it
/// took, as a line that never jumps: what [SdlAudioSink#queuedSamples()] subtracts
/// from the raw queue, and so what the audio clock moves by (ADR-0463, ADR-0485).
///
/// ## What is measured
///
/// A pull is seen when the raw queue shrinks. From the moment it is seen, the
/// device plays it out at the stream's rate, so the **measurement** is the
/// samples taken up to the last pull, plus what has been played of that pull
/// since: `taken + min(elapsed × rate, pull)`. That is exact when the pulls come
/// on time. They do not. CoreAudio's arrive early and late by a good part of a
/// pull, and two can come close together. A pull that comes early makes the
/// measurement jump forward by the part of the last pull it had not yet played,
/// up to a whole pull: 21 ms at 48 kHz, more than a picture lasts at 60 fps. A
/// clock that jumps by that passes over a picture.
///
/// ## What is reported
///
/// The **estimate** runs at the stream's rate and is steered toward the
/// measurement, by at most [#MAX_SLEW] faster or slower, in proportion to how
/// far it is off. So an early pull is caught up over a tenth of a second or so,
/// and never in a step. It is held back so that it never gets more than a pull
/// past what the device has taken: a late pull stalls it, and a stall passes over
/// no picture. When it is off by more than [#SNAP_NANOS] of audio -- an
/// underrun, or pulls that went unseen because nothing asked for a while -- it
/// goes straight to the measurement. On average the two are the same line, so
/// the latency the sink reports holds for both.
///
/// Not thread-safe: the sink holds its monitor around every call. Times are on
/// [System#nanoTime()]'s scale, passed in, so a test decides them.
final class DrainEstimate {

    /// The most faster or slower than the stream's rate the estimate is steered.
    static final double MAX_SLEW = 0.1;

    /// The error past which the estimate goes straight to the measurement, in
    /// nanoseconds of audio: several pulls, and more than pulls that come early
    /// or late ever are.
    static final long SNAP_NANOS = 100_000_000L;

    /// The error at which the estimate is steered by [#MAX_SLEW], in nanoseconds
    /// of audio: an early pull's usual 10 ms is caught up at full slew, and a
    /// millisecond's at a tenth of it.
    static final long STEER_NANOS = 10_000_000L;

    /// How many pulls [#typicalPull()] is the median of.
    static final int RECENT_PULLS = 15;

    private final int sampleRate;
    private double rate = 1;

    /// Samples the device has taken since the last reset, by the pulls seen.
    private long taken;
    /// The size of the last pull seen: two pulls between two readings are seen
    /// as one of twice the size.
    private long pull;
    /// The sizes of the last [#RECENT_PULLS] pulls seen, for [#typicalPull()].
    private final long[] recent = new long[RECENT_PULLS];
    private int recentCount;
    /// When the last pull was seen.
    private long pulledAt;

    /// The estimate: samples played since the last reset, and when it was.
    private double estimate;
    private long estimatedAt;

    /// While paused, when the pause began; otherwise negative.
    private long pausedAt = -1;

    DrainEstimate(int sampleRate, long now) {
        if (sampleRate <= 0) {
            throw new IllegalArgumentException("sample rate " + sampleRate);
        }
        this.sampleRate = sampleRate;
        reset(now);
    }

    /// Back to nothing taken and nothing played: a queue just opened or cleared.
    /// A pause stays a pause.
    void reset(long now) {
        taken = 0;
        pull = 0;
        recentCount = 0;
        pulledAt = now;
        estimate = 0;
        estimatedAt = now;
        if (pausedAt >= 0) {
            pausedAt = now;
        }
    }

    /// The device took `samples` in one pull, seen at `now`.
    void pulled(long samples, long now) {
        advance(now);
        var first = recentCount == 0;
        taken += samples;
        pull = samples;
        pulledAt = now;
        recent[recentCount % RECENT_PULLS] = samples;
        recentCount++;
        // The first pull since a reset is where the device starts: there is
        // nothing to be smooth with yet. After that, only far behind -- pulls
        // that went unseen -- does it go straight there.
        if (first || measured(now) - estimate > SNAP_NANOS * perNano()) {
            estimate = measured(now);
        }
    }

    /// How many samples of what the device has taken the estimate says are
    /// played, at `now`: at most one pull past the last pull seen.
    long drained(long now) {
        advance(now);
        return Math.round(estimate) - taken;
    }

    /// The size of a pull, as the median of the last few seen, or 0 before the
    /// first: what the device takes at a time. A reading that saw two pulls at
    /// once does not double it, so the latency counted in pulls does not jump.
    long typicalPull() {
        var count = Math.min(recentCount, RECENT_PULLS);
        if (count == 0) {
            return 0;
        }
        var sizes = java.util.Arrays.copyOf(recent, count);
        java.util.Arrays.sort(sizes);
        return sizes[count / 2];
    }

    /// Stands the estimate and the measurement still from `now`.
    void pause(long now) {
        if (pausedAt < 0) {
            advance(now);
            pausedAt = now;
        }
    }

    /// Lets them run again from `now`, from where they stood.
    void resume(long now) {
        if (pausedAt >= 0) {
            var stood = now - pausedAt;
            pulledAt += stood;
            estimatedAt = now;
            pausedAt = -1;
        }
    }

    /// Plays at `rate` times the stream's speed from `now`. What has been played
    /// of the current pull stays played.
    void rate(double rate, long now) {
        if (!(rate > 0)) {
            throw new IllegalArgumentException("rate " + rate);
        }
        advance(now);
        if (pausedAt < 0) {
            // The measurement keeps what it has drained of the pull, and drains
            // the rest at the new rate.
            pulledAt = now - Math.round((now - pulledAt) * (this.rate / rate));
        }
        this.rate = rate;
    }

    /// The measurement at `now`: what has been taken up to the last pull, and
    /// what has been played of it since, at the stream's rate.
    double measured(long now) {
        var since = (pausedAt >= 0 ? pausedAt : now) - pulledAt;
        var played = Math.min(Math.max(since, 0) * perNano(), (double) pull);
        return taken + played;
    }

    /// Moves the estimate on to `now`, steered toward the measurement, never
    /// back, and never more than a pull past what has been taken.
    private void advance(long now) {
        if (pausedAt >= 0 || now <= estimatedAt) {
            return;
        }
        var target = measured(now);
        var step = (now - estimatedAt) * perNano();
        // How far off the estimate would be at the stream's rate alone.
        var error = target - (estimate + step);
        if (Math.abs(error) > SNAP_NANOS * perNano()) {
            estimate = target;
        } else {
            var slew = Math.max(-MAX_SLEW, Math.min(MAX_SLEW, MAX_SLEW * error / (STEER_NANOS * perNano())));
            var next = estimate + step * (1 + slew);
            estimate = Math.max(estimate, Math.min(next, taken + (double) pull));
        }
        estimatedAt = now;
    }

    private double perNano() {
        return sampleRate * rate / 1e9;
    }
}
