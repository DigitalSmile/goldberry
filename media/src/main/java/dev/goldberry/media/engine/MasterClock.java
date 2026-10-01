package dev.goldberry.media.engine;

import java.util.Objects;
import java.util.function.LongSupplier;

import org.jspecify.annotations.Nullable;

import dev.goldberry.media.MediaClock;

/// The time pictures are presented against (`docs/goldberry-media.md` §3,
/// "Master clock"), in nanoseconds of stream time.
///
/// Two sources, one at a time:
///
/// - **The audio clock**, while an audio track plays. It is what the sink has
///   played, so pausing the sink stops it and a seek resets it; nothing here
///   has to be told.
/// - **A free-running clock** otherwise: a stream position plus the time that
///   has passed on a [MediaClock] since it was anchored. It runs only when told
///   to, so the Engine holds it while buffering, while paused, and while a seek
///   settles. A source with no audio uses it from the start, and a video whose
///   audio has ended hands over to it at the audio's last position
///   ([#freeRunFrom]).
///
/// At a [#setRate] other than 1 the free-running clock counts stream time that
/// much faster than the [MediaClock] passes.
///
/// Thread-safe: the decode threads and the UI thread read it, and the Engine's
/// threads steer it.
final class MasterClock {

    private final MediaClock time;
    private @Nullable LongSupplier audio;
    private long base;
    private long anchoredAt;
    private boolean running;
    private double rate = 1;

    /// A free-running clock at zero, held, reading `time`.
    MasterClock(MediaClock time) {
        this.time = Objects.requireNonNull(time, "time");
    }

    /// What is playing now.
    synchronized long nanos() {
        if (audio != null) {
            return audio.getAsLong();
        }
        return running ? base + elapsed() : base;
    }

    /// Runs the free-running clock `rate` times as fast from now on. What it has
    /// counted so far stays counted at the rate it ran at. The audio clock needs
    /// no telling: the sink plays faster, and it is made of the sink.
    synchronized void setRate(double rate) {
        if (!(rate > 0)) {
            throw new IllegalArgumentException("rate " + rate);
        }
        if (running) {
            base += elapsed();
            anchoredAt = time.nanoTime();
        }
        this.rate = rate;
    }

    /// Makes the audio clock the master.
    synchronized void followAudio(LongSupplier audioClock) {
        audio = Objects.requireNonNull(audioClock, "audioClock");
    }

    /// Whether the audio clock is the master.
    synchronized boolean followingAudio() {
        return audio != null;
    }

    /// Leaves the audio clock at `positionNanos` and runs free from there, if
    /// `run`: the audio has ended and the picture has not.
    synchronized void freeRunFrom(long positionNanos, boolean run) {
        audio = null;
        base = positionNanos;
        anchoredAt = time.nanoTime();
        running = run;
    }

    /// Moves the free-running clock to `positionNanos`, held until [#run()].
    synchronized void set(long positionNanos) {
        base = positionNanos;
        running = false;
    }

    /// Starts the free-running clock from where it stands.
    synchronized void run() {
        if (!running) {
            anchoredAt = time.nanoTime();
            running = true;
        }
    }

    /// Stops the free-running clock where it stands.
    synchronized void hold() {
        if (running) {
            base += elapsed();
            running = false;
        }
    }

    /// Stream time since the anchor: wall time at the rate. Called with the
    /// monitor held.
    private long elapsed() {
        return Math.round(Math.max(0, time.nanoTime() - anchoredAt) * rate);
    }

    /// Whether the clock is moving on its own: always, while audio is the master,
    /// as far as this class can tell, and while running otherwise.
    synchronized boolean running() {
        return audio != null || running;
    }
}
