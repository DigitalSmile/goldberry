package io.github.digitalsmile.goldberry.backend.headless;

import io.github.digitalsmile.goldberry.backend.Backend;
import io.github.digitalsmile.goldberry.backend.BackendEvent;
import io.github.digitalsmile.goldberry.backend.BackendException;
import io.github.digitalsmile.goldberry.backend.BackendPopup;
import io.github.digitalsmile.goldberry.backend.BackendWindow;
import io.github.digitalsmile.goldberry.backend.DisplayScale;
import io.github.digitalsmile.goldberry.backend.EventSink;
import io.github.digitalsmile.goldberry.backend.PopupSpec;
import io.github.digitalsmile.goldberry.backend.WindowSpec;
import io.github.digitalsmile.goldberry.natives.log.Logs;
import java.time.Duration;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Objects;
import java.util.Queue;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.LockSupport;
import org.slf4j.Logger;

/// A backend with no platform underneath it.
///
/// Serves two purposes. It is the target for golden-image tests, which run
/// identically on all three OSes because nothing platform-specific participates
/// (`docs/ARCHITECTURE.md` §14). And it is how the SPI itself is testable before
/// any real backend exists: every rule the interfaces state — UI-thread
/// confinement, frame coalescing, damage bounds, buffer size agreement — is
/// enforced here and asserted against.
///
/// Frames are kept rather than drawn. [HeadlessWindow#lastFrame()] is what a test
/// asserts on.
///
/// Events are injected, not observed: [#post] queues one for the next
/// [#pumpEvents]. A test drives the same code path a real backend drives.
public final class HeadlessBackend implements Backend {

    private static final Logger LOG = Logs.of(HeadlessBackend.class);

    /// The scale new windows get. Deliberately fractional in tests elsewhere:
    /// 100% is the scale at which every HiDPI bug hides.
    private final DisplayScale scale;

    /// The UI thread. Captured at construction rather than configured, which is
    /// what makes "the thread that created the backend" checkable instead of
    /// merely documented.
    private final Thread uiThread;

    private final List<HeadlessWindow> windows = new ArrayList<>();
    private final Queue<BackendEvent> pending = new ArrayDeque<>();

    /// Set by [#wakeup()], which is the one thing another thread may call, so it
    /// is the one piece of state that has to be safe to touch from anywhere.
    private final AtomicBoolean woken = new AtomicBoolean();

    private boolean closed;

    public HeadlessBackend() {
        this(DisplayScale.ONE);
    }

    public HeadlessBackend(DisplayScale scale) {
        this.scale = Objects.requireNonNull(scale, "scale");
        this.uiThread = Thread.currentThread();
    }

    @Override
    public String name() {
        return "headless";
    }

    @Override
    public BackendWindow createWindow(WindowSpec spec) {
        requireUiThread();
        requireOpen();
        Objects.requireNonNull(spec, "spec");

        var window = new HeadlessWindow(this, spec, scale);
        windows.add(window);
        LOG.debug("created headless window \"{}\" {} at {}", spec.title(), spec.size(), scale);
        return window;
    }

    /// A popup, which this backend has because the SPI's rules need somewhere to
    /// be checked without a display.
    ///
    /// Never empty, unlike a real driver's: a backend with no platform under it
    /// has no driver to lack the capability. The `Optional` is still the SPI's,
    /// and [io.github.digitalsmile.goldberry.backend.sdl3.Sdl3Backend] is where it
    /// is genuinely empty — under SDL's `dummy` driver, which has no popups.
    @Override
    public Optional<BackendPopup> createPopup(BackendWindow owner, PopupSpec spec) {
        requireUiThread();
        requireOpen();
        Objects.requireNonNull(owner, "owner");
        Objects.requireNonNull(spec, "spec");
        if (!(owner instanceof HeadlessWindow parent)) {
            throw new IllegalArgumentException(
                    "a popup's owner must be a window from this backend, and " + owner
                            + " is not");
        }
        if (!parent.isOpen()) {
            throw new IllegalStateException("cannot open a popup on a window that has closed");
        }

        var popup = new HeadlessPopup(this, parent, spec, scale);
        windows.add(popup);
        LOG.debug("created headless {} popup {} at {}", spec.kind(), spec.size(), spec.position());
        return Optional.of(popup);
    }

    @Override
    public List<BackendWindow> windows() {
        requireUiThread();
        return List.copyOf(windows);
    }

    @Override
    public int pumpEvents(EventSink sink, Duration timeout) {
        requireUiThread();
        requireOpen();
        Objects.requireNonNull(sink, "sink");
        Objects.requireNonNull(timeout, "timeout");
        if (timeout.isNegative()) {
            throw new IllegalArgumentException("timeout must not be negative: " + timeout);
        }

        // A real backend blocks in the platform's event queue here. With nothing
        // to block on, parking for the timeout is what keeps a test's frame loop
        // from spinning -- and it is interruptible by wakeup() for the same
        // reason the real one is.
        if (pending.isEmpty() && !timeout.isZero() && !woken.get()) {
            var deadline = System.nanoTime() + timeout.toNanos();
            while (pending.isEmpty() && !woken.get()) {
                var remaining = deadline - System.nanoTime();
                if (remaining <= 0) {
                    break;
                }
                LockSupport.parkNanos(this, remaining);
                if (Thread.interrupted()) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
        }
        woken.set(false);

        // Drained into a list first: a sink that posts while handling an event
        // must not extend the pump it is inside, or a self-posting handler never
        // returns.
        var batch = new ArrayList<BackendEvent>(pending.size());
        while (!pending.isEmpty()) {
            batch.add(pending.poll());
        }
        for (var event : batch) {
            // A frame request is satisfied by its event being delivered, so a
            // repaint asked for inside the handler survives into the next pump.
            if (event instanceof BackendEvent.FrameDue frame
                    && frame.window() instanceof HeadlessWindow window) {
                window.frameDelivered();
            }
            // A popup's requested size becomes its actual size here, which is
            // where a window manager's answer would arrive. See HeadlessPopup.
            if (event instanceof BackendEvent.Resized resized
                    && resized.window() instanceof HeadlessPopup popup) {
                popup.resizeDelivered();
            }
            sink.accept(event);
        }
        return batch.size();
    }

    @Override
    public void wakeup() {
        // No requireUiThread: this is the one method other threads may call.
        woken.set(true);
        LockSupport.unpark(uiThread);
    }

    @Override
    public void close() {
        if (closed) {
            return;
        }
        requireUiThread();
        closed = true;
        // Copied, because HeadlessWindow.close() removes itself from the list.
        for (var window : List.copyOf(windows)) {
            window.close();
        }
        windows.clear();
        pending.clear();
    }

    /// Queues an event for the next [#pumpEvents].
    ///
    /// The headless equivalent of the platform doing something. Tests use it to
    /// drive resizes, scale changes and close requests through the same path a
    /// real backend would.
    public void post(BackendEvent event) {
        requireUiThread();
        requireOpen();
        pending.add(Objects.requireNonNull(event, "event"));
    }

    /// How many events are waiting.
    public int pendingEventCount() {
        requireUiThread();
        return pending.size();
    }

    /// Whether this backend has been closed.
    public boolean isClosed() {
        return closed;
    }

    void forget(HeadlessWindow window) {
        windows.remove(window);
        pending.removeIf(event -> event.window() == window);
    }

    void requireUiThread() {
        if (Thread.currentThread() != uiThread) {
            throw new BackendException(
                    "the backend was called from " + Thread.currentThread().getName()
                            + " but belongs to " + uiThread.getName()
                            + ". Every call except wakeup() must be on the UI thread"
                            + " -- AppKit requires it, so the SPI requires it everywhere.");
        }
    }

    private void requireOpen() {
        if (closed) {
            throw new BackendException("the headless backend is closed");
        }
    }
}
