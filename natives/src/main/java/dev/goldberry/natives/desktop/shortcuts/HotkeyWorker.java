package dev.goldberry.natives.desktop.shortcuts;

import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;

import dev.goldberry.log.Logs;

/// A backend with a thread of its own, which owns the platform's connection
/// and is the only thread that touches it: Xlib's display, libdbus' private
/// connection, a Windows thread's message queue.
///
/// Work handed in from another thread goes on a queue, the thread is woken,
/// and runs it between waits. A backend says how to wait for its events and
/// how to wake the wait; this class does the rest.
abstract class HotkeyWorker implements HotkeyBackend {

    private static final Logger LOG = Logs.of(HotkeyWorker.class);

    private final ConcurrentLinkedQueue<Runnable> tasks = new ConcurrentLinkedQueue<>();
    private final CountDownLatch opened = new CountDownLatch(1);
    private final Thread thread;
    private volatile boolean open;
    private volatile boolean closing;
    private @Nullable HotkeyState state;

    HotkeyWorker(String threadName) {
        this.thread = Thread.ofPlatform().daemon().name(threadName).unstarted(this::loop);
    }

    /// Starts the thread and waits for it to open the connection.
    ///
    /// @return whether it did
    final boolean start() {
        thread.start();
        try {
            opened.await(5, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        return open;
    }

    /// Opens the connection, on the worker's thread.
    abstract boolean openOnThread();

    /// Waits for events or a wake, and handles what arrived, on the worker's
    /// thread.
    abstract void awaitEvents();

    /// Wakes [#awaitEvents] from another thread.
    abstract void wake();

    /// Binds `hotkeys` in place of what was bound, on the worker's thread.
    /// `state` is already the new one. Completes `outcome` now or later.
    abstract void bindOnThread(List<Hotkey> hotkeys, HotkeyState state, CompletableFuture<List<HotkeyOutcome>> outcome);

    /// Unbinds everything and closes the connection, on the worker's thread.
    abstract void closeOnThread();

    /// The state of the current binding, for the event handlers. Null before
    /// the first bind.
    final @Nullable HotkeyState state() {
        return state;
    }

    @Override
    public final boolean bindsOnMainThread() {
        return false;
    }

    @Override
    public final CompletableFuture<List<HotkeyOutcome>> bind(List<Hotkey> hotkeys, HotkeyListener listener) {
        var copy = List.copyOf(hotkeys);
        Objects.requireNonNull(listener, "listener");
        var outcome = new CompletableFuture<List<HotkeyOutcome>>();
        if (closing || !open) {
            outcome.complete(HotkeyBackend.refuseAll(copy, HotkeyOutcome.Refusal.UNSUPPORTED, name() + " is closed"));
            return outcome;
        }
        submit(() -> {
            if (closing) {
                outcome.complete(
                        HotkeyBackend.refuseAll(copy, HotkeyOutcome.Refusal.UNSUPPORTED, name() + " is closed"));
                return;
            }
            var previous = state;
            if (previous != null) {
                previous.releaseAll();
            }
            var next = new HotkeyState(listener);
            state = next;
            try {
                bindOnThread(copy, next, outcome);
            } catch (RuntimeException e) {
                LOG.debug("{} could not bind", name(), e);
                outcome.complete(HotkeyBackend.refuseAll(copy, HotkeyOutcome.Refusal.UNSUPPORTED, e.toString()));
            }
        });
        return outcome;
    }

    /// Runs `task` on the worker's thread.
    final void submit(Runnable task) {
        tasks.add(task);
        wake();
    }

    final boolean isClosing() {
        return closing;
    }

    @Override
    public final void close() {
        if (closing) {
            return;
        }
        closing = true;
        wake();
        if (Thread.currentThread() != thread) {
            try {
                thread.join(TimeUnit.SECONDS.toMillis(5));
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
    }

    private void loop() {
        try {
            open = openOnThread();
        } catch (RuntimeException e) {
            LOG.debug("{} could not open", name(), e);
            open = false;
        } finally {
            opened.countDown();
        }
        if (!open) {
            return;
        }
        try {
            while (!closing) {
                runTasks();
                if (!closing) {
                    awaitEvents();
                }
            }
            runTasks();
        } catch (RuntimeException e) {
            LOG.warn("{} stopped", name(), e);
        } finally {
            var current = state;
            if (current != null) {
                current.releaseAll();
            }
            try {
                closeOnThread();
            } catch (RuntimeException e) {
                LOG.debug("{} did not close cleanly", name(), e);
            }
            open = false;
        }
    }

    private void runTasks() {
        Runnable task;
        while ((task = tasks.poll()) != null) {
            try {
                task.run();
            } catch (RuntimeException e) {
                LOG.warn("{}: a task failed", name(), e);
            }
        }
    }
}
