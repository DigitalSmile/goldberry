package io.github.digitalsmile.goldberry.backend;

import java.time.Duration;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Supplier;

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

    /// How long a single pump waits before returning with nothing.
    ///
    /// Not a frame budget — the loop wakes on any event, and [Backend#wakeup()]
    /// interrupts it immediately. It is a heartbeat, so a missed wakeup costs a
    /// second of latency rather than a hang.
    private static final Duration IDLE_TIMEOUT = Duration.ofSeconds(1);

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
        try {
            while (running) {
                // Before the pump: work queued while the loop was blocked should
                // be visible to the frame this pump produces, not the next one.
                ui.drain();
                if (!running) {
                    break;
                }

                backend.pumpEvents(sink, IDLE_TIMEOUT);

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
