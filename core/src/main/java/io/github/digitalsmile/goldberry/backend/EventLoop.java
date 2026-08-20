package io.github.digitalsmile.goldberry.backend;

import io.github.digitalsmile.goldberry.natives.log.Logs;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Supplier;
import org.slf4j.Logger;

/// Drives a backend: drains queued UI work, pumps platform events, repeats.
///
/// ## The threading rule
///
/// One thread runs the UI and nothing else runs on it. That thread is only ever
/// *waiting* — inside the platform's event queue — or *working* on something
/// short. Anything that could take longer than a frame belongs on a background
/// thread, and comes back through [#supplyAsync].
///
/// [#supplyAsync] runs work on a virtual thread and completes its future **on the
/// UI thread**, so every `thenAccept` downstream is already where it needs to be
/// to touch a window. That is the whole ceremony: no `invokeLater`, no manual
/// hand-off, no rule to remember about which callback runs where.
///
/// Virtual threads rather than a pool because the work this exists for is mostly
/// waiting — reading a file, fetching a resource — and a pool sized for
/// throughput is the wrong shape for that. Blocking a virtual thread costs a
/// continuation, not an OS thread.
///
/// ## What still blocks
///
/// Rasterization does not happen here. Blend2D rasterizes on its own worker
/// threads (§5) and the UI thread hands over a finished buffer, so a slow frame
/// costs a frame rather than the event loop.
public final class EventLoop implements AutoCloseable {

    private static final Logger LOG = Logs.of(EventLoop.class);

    /// How long a single pump waits before returning with nothing.
    ///
    /// Not a frame budget — the loop wakes on any event, and [Backend#wakeup()]
    /// interrupts it immediately. It is a heartbeat, so a missed wakeup costs a
    /// second of latency rather than a hang.
    private static final Duration IDLE_TIMEOUT = Duration.ofSeconds(1);

    /// What [#after] has scheduled, in no particular order — there are never many,
    /// and a heap would be machinery for a list that is usually empty and rarely
    /// longer than one.
    private final List<Timer> timers = new ArrayList<>();

    private final Backend backend;
    private final UiExecutor ui;
    private final ExecutorService background;

    private volatile boolean running;
    private boolean closed;

    public EventLoop(Backend backend) {
        this.backend = Objects.requireNonNull(backend, "backend");
        this.ui = new UiExecutor(backend::wakeup);
        this.background = Executors.newVirtualThreadPerTaskExecutor();
    }

    /// The queue onto the UI thread.
    public UiExecutor ui() {
        return ui;
    }

    /// Runs until [#stop()] or until every window has closed.
    ///
    /// @throws BackendException if called off the UI thread
    public void run(EventSink sink) {
        ui.requireUiThread();
        Objects.requireNonNull(sink, "sink");
        if (running) {
            throw new BackendException("the event loop is already running");
        }

        running = true;
        LOG.debug("event loop started on {}", Thread.currentThread().getName());
        try {
            while (running) {
                // Before the pump: work queued while the loop was blocked should
                // be visible to the frame this pump produces, not the next one.
                ui.drain();
                if (!running) {
                    break;
                }

                // Shortened when a timer is due sooner than the heartbeat, so a
                // 400ms tooltip delay does not wait a second for the pump to
                // return on an otherwise idle desktop.
                backend.pumpEvents(sink, nextTimeout());

                fireDueTimers();

                // After: handlers post work too, and it should not wait for the
                // next platform event to arrive -- which, on an idle desktop, may
                // be never.
                ui.drain();

                if (backend.windows().isEmpty()) {
                    running = false;
                }
            }
        } finally {
            running = false;
            LOG.debug("event loop finished");
        }
    }

    /// Runs `action` on the UI thread after `delay`.
    ///
    /// The loop's own timer, and it is the loop's because the loop is the thing
    /// that is asleep: a delay implemented by sleeping somewhere else would fire
    /// on time and then wait up to [#IDLE_TIMEOUT] for the pump to come back and
    /// notice. This shortens the pump instead.
    ///
    /// Two consumers, both named in `docs/core-widgets.md`: a `tooltip` "shows on
    /// hover *and on keyboard focus* **after delay**", and a submenu opens on
    /// "hover-intent timing". A toast's timeout is the third.
    ///
    /// UI thread only. Work that arrives from elsewhere goes through [#ui()],
    /// which is the one door in.
    ///
    /// @param delay  how long to wait; zero or negative fires on the next
    ///               iteration rather than immediately, because "later" is the
    ///               only thing a caller can mean by asking a loop
    /// @param action what to run, on the UI thread
    /// @return a handle that cancels it
    public Timer after(Duration delay, Runnable action) {
        ui.requireUiThread();
        Objects.requireNonNull(delay, "delay");
        Objects.requireNonNull(action, "action");
        var timer = new Timer(System.nanoTime() + Math.max(0L, delay.toNanos()), action);
        timers.add(timer);
        // The loop may be parked in `pumpEvents` with a timeout longer than this
        // delay -- which is the ordinary case, since the heartbeat is a second.
        backend.wakeup();
        return timer;
    }

    /// How long the next pump may block for: the heartbeat, or the wait until the
    /// earliest timer, whichever is sooner.
    private Duration nextTimeout() {
        if (timers.isEmpty()) {
            return IDLE_TIMEOUT;
        }
        var now = System.nanoTime();
        var earliest = Long.MAX_VALUE;
        for (var timer : timers) {
            if (!timer.cancelled) {
                earliest = Math.min(earliest, timer.dueNanos);
            }
        }
        if (earliest == Long.MAX_VALUE) {
            return IDLE_TIMEOUT;
        }
        var remaining = earliest - now;
        return remaining <= 0
                ? Duration.ZERO
                : Duration.ofNanos(Math.min(remaining, IDLE_TIMEOUT.toNanos()));
    }

    /// Runs whatever is due, and drops it.
    ///
    /// Collected before running: a timer's action may schedule another, and a
    /// tooltip's does — an action that added itself to the list being walked would
    /// fire in the same iteration for ever.
    private void fireDueTimers() {
        if (timers.isEmpty()) {
            return;
        }
        var now = System.nanoTime();
        var due = new ArrayList<Timer>();
        for (var iterator = timers.iterator(); iterator.hasNext();) {
            var timer = iterator.next();
            if (timer.cancelled) {
                iterator.remove();
            } else if (timer.dueNanos <= now) {
                iterator.remove();
                due.add(timer);
            }
        }
        for (var timer : due) {
            if (!timer.cancelled) {
                timer.action.run();
            }
        }
    }

    /// One pending [#after].
    ///
    /// A class rather than a `Future`: the only thing a caller ever does with one
    /// is cancel it, and a hover that ends before the delay is up cancels one on
    /// every pointer move.
    public static final class Timer {

        private final long dueNanos;
        private final Runnable action;
        private boolean cancelled;

        /// Package-private rather than private, so that a test fixture in this
        /// package can hand one out for a fake event loop — the same privilege
        /// `TestFrames` has over `Frame`'s constructor, and for the same reason:
        /// widening it to public would put a scheduling internal in the toolkit's
        /// API for the sake of a test helper.
        Timer(long dueNanos, Runnable action) {
            this.dueNanos = dueNanos;
            this.action = action;
        }

        /// Stops it firing. Idempotent, and harmless after it already has.
        public void cancel() {
            cancelled = true;
        }

        /// Whether it is still going to fire.
        public boolean isPending() {
            return !cancelled;
        }
    }

    /// Asks the loop to finish after the current iteration.
    ///
    /// Safe from any thread: it sets a flag and wakes the loop, which is the one
    /// thing [Backend#wakeup()] promises to allow.
    public void stop() {
        running = false;
        backend.wakeup();
    }

    public boolean isRunning() {
        return running;
    }

    /// Throws if the loop is already running.
    ///
    /// Lets a caller refuse before doing setup work that would be wasted, rather
    /// than discovering it inside [#run].
    public void requireNotRunning() {
        if (running) {
            throw new BackendException("the event loop is already running");
        }
    }

    /// Runs `work` on a virtual thread and completes the result on the UI thread.
    ///
    /// The returned future's callbacks therefore run where they can touch
    /// windows. Failures arrive the same way, as an exceptional completion rather
    /// than a stack trace on a thread nobody is watching.
    public <T> CompletableFuture<T> supplyAsync(Supplier<T> work) {
        Objects.requireNonNull(work, "work");
        var future = new CompletableFuture<T>();
        background.execute(() -> {
            try {
                var value = work.get();
                ui.execute(() -> future.complete(value));
            } catch (Throwable t) {
                ui.execute(() -> future.completeExceptionally(t));
            }
        });
        return future;
    }

    /// Runs `work` on a virtual thread, with nothing to return.
    public CompletableFuture<Void> runAsync(Runnable work) {
        Objects.requireNonNull(work, "work");
        return supplyAsync(() -> {
            work.run();
            return null;
        });
    }

    /// Stops the loop and shuts down the background threads. Idempotent.
    ///
    /// Does not wait for background work: a task still running when the UI is
    /// gone has nowhere to deliver its result, and blocking shutdown on it is how
    /// an application fails to exit.
    @Override
    public void close() {
        if (closed) {
            return;
        }
        closed = true;
        running = false;
        background.shutdownNow();
    }
}
