package io.github.digitalsmile.goldberry.backend;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.Executor;

/// The way onto the UI thread from anywhere else.
///
/// Goldberry's UI thread is single and non-negotiable — AppKit requires window
/// calls on the process's first thread, so the SPI requires them on one thread
/// everywhere (ADR-0019). That leaves one question: how does work finishing on a
/// background thread get its result onto the UI thread safely?
///
/// This is the answer, and the only one. [#execute] queues a task from any thread
/// and wakes the event loop; the loop drains the queue on the UI thread between
/// pumps. No lock is held while a task runs, so a task may queue more.
///
/// The queue is unbounded on purpose. Bounding it would mean either blocking a
/// background thread — reintroducing the stall this exists to prevent — or
/// dropping UI updates, which is worse. A producer that outruns the UI thread is
/// a bug in the producer, and one that shows up as memory growth rather than as
/// a mysterious freeze.
public final class UiExecutor implements Executor {

    private final Thread uiThread;
    private final Runnable wakeup;
    private final ConcurrentLinkedQueue<Runnable> tasks = new ConcurrentLinkedQueue<>();

    /// @param wakeup how to interrupt an idle event loop — [Backend#wakeup()]
    public UiExecutor(Runnable wakeup) {
        this.uiThread = Thread.currentThread();
        this.wakeup = Objects.requireNonNull(wakeup, "wakeup");
    }

    /// Queues `task` to run on the UI thread. Safe from any thread, including the
    /// UI thread itself — a task posted from a handler runs on the next drain
    /// rather than re-entering the current one.
    @Override
    public void execute(Runnable task) {
        Objects.requireNonNull(task, "task");
        tasks.add(task);
        // Ordering matters: enqueue, then wake. The other way round races -- the
        // loop could wake, find nothing, and go back to sleep before the task
        // lands.
        wakeup.run();
    }

    /// Whether the caller is on the UI thread.
    public boolean isUiThread() {
        return Thread.currentThread() == uiThread;
    }

    /// Throws unless the caller is on the UI thread.
    public void requireUiThread() {
        if (!isUiThread()) {
            throw new BackendException(
                    "this must run on the UI thread (" + uiThread.getName() + "), not "
                            + Thread.currentThread().getName()
                            + ". Post it with UiExecutor.execute(...) instead.");
        }
    }

    /// Runs everything queued, on the UI thread.
    ///
    /// Drains a snapshot rather than looping until empty: a task that posts
    /// another must not be able to hold the loop indefinitely, or one
    /// self-scheduling animation starves every event.
    ///
    /// A task that throws propagates, and the tasks after it in the same batch
    /// still run — one broken handler should not silently swallow the rest of
    /// the frame's work.
    ///
    /// @return how many tasks ran
    public int drain() {
        requireUiThread();

        var batch = new ArrayList<Runnable>();
        Runnable task;
        while ((task = tasks.poll()) != null) {
            batch.add(task);
        }

        List<Throwable> failures = null;
        for (var queued : batch) {
            try {
                queued.run();
            } catch (Throwable t) {
                if (failures == null) {
                    failures = new ArrayList<>();
                }
                failures.add(t);
            }
        }
        if (failures != null) {
            var first = failures.getFirst();
            var error = new BackendException(
                    failures.size() + " queued UI task(s) failed", first);
            failures.stream().skip(1).forEach(error::addSuppressed);
            throw error;
        }
        return batch.size();
    }

    /// How many tasks are waiting.
    public int pendingTaskCount() {
        return tasks.size();
    }
}
