package dev.goldberry.media.engine;

import java.util.Objects;

import dev.goldberry.media.MediaClock;

/// The last samples of a track on their way to the ear, after the sink's queue
/// has emptied.
///
/// The audio clock is what has left the queue less the device's latency. Once
/// the queue is empty nothing leaves it any more, so on its own the clock would
/// stop a latency short of the end, and the track would end before its last
/// 200 ms had been heard over Bluetooth. The tail is the time since the queue
/// emptied, which the clock takes off the latency: it runs on to the end in
/// wall time, and the audio is done when the tail has lasted the latency.
///
/// Paused, the tail stands still, as the queue does. Any write or seek ends it:
/// there is a queue again.
///
/// Thread-safe: the audio thread starts it, the Engine pauses it, and any
/// thread reading the clock reads it.
final class AudioTail {

    /// What [#elapsedNanos()] answers with no tail.
    static final long NONE = -1;

    private final MediaClock time;
    private boolean started;
    private long startedAt;
    /// While paused, how long the tail had lasted; [#NONE] while running.
    private long frozen = NONE;

    AudioTail(MediaClock time) {
        this.time = Objects.requireNonNull(time, "time");
    }

    /// The queue has just emptied at the end of the track. A paused Engine
    /// starts the tail frozen at nothing.
    synchronized void start(boolean paused) {
        started = true;
        startedAt = time.nanoTime();
        frozen = paused ? 0 : NONE;
    }

    /// There is a queue again: a write, or a seek.
    synchronized void reset() {
        started = false;
        frozen = NONE;
    }

    /// Stands still where it is.
    synchronized void pause() {
        if (started && frozen == NONE) {
            frozen = Math.max(time.nanoTime() - startedAt, 0);
        }
    }

    /// Runs on from where it stood.
    synchronized void resume() {
        if (started && frozen != NONE) {
            startedAt = time.nanoTime() - frozen;
            frozen = NONE;
        }
    }

    /// How long, in wall-clock nanoseconds, the queue has been empty and
    /// playing, or [#NONE] when it is not empty.
    synchronized long elapsedNanos() {
        if (!started) {
            return NONE;
        }
        return frozen != NONE ? frozen : Math.max(time.nanoTime() - startedAt, 0);
    }
}
