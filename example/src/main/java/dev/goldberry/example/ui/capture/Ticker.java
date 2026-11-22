package dev.goldberry.example.ui.capture;

import java.time.Duration;
import java.util.Objects;

import org.jspecify.annotations.Nullable;

import dev.goldberry.Host;
import dev.goldberry.render.event.EventLoop;

/// Runs an action every `interval` on the UI thread until stopped: the clock a
/// synthetic source has none of, since it delivers only when advanced.
///
/// One timer at a time, rescheduled as each fires, as the media cards' poll
/// is. A window with no host (a picture of a card) never ticks.
final class Ticker implements AutoCloseable {

    private final Duration interval;
    private final Runnable action;
    private @Nullable Host host;
    private EventLoop.@Nullable Timer timer;
    private boolean running;

    /// A ticker that runs `action` every `interval` once started.
    Ticker(Duration interval, Runnable action) {
        if (interval.isNegative() || interval.isZero()) {
            throw new IllegalArgumentException("a ticker ticks every positive interval, not " + interval);
        }
        this.interval = interval;
        this.action = Objects.requireNonNull(action, "action");
    }

    /// The interval each tick stands for.
    Duration interval() {
        return interval;
    }

    /// Whether it is ticking.
    boolean running() {
        return running;
    }

    /// Starts ticking on `host`'s loop; does nothing with no host or when it
    /// already ticks.
    void start(@Nullable Host on) {
        if (running || on == null) {
            return;
        }
        host = on;
        running = true;
        schedule();
    }

    /// Stops ticking. Idempotent.
    @Override
    public void close() {
        running = false;
        var pending = timer;
        timer = null;
        if (pending != null) {
            pending.cancel();
        }
    }

    private void schedule() {
        var on = host;
        if (!running || on == null) {
            return;
        }
        timer = on.after(interval, () -> {
            timer = null;
            if (running) {
                action.run();
                schedule();
            }
        });
    }
}
