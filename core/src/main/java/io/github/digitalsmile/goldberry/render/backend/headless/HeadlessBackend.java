package io.github.digitalsmile.goldberry.render.backend.headless;

import java.time.Duration;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Queue;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.LockSupport;

import org.slf4j.Logger;

import io.github.digitalsmile.goldberry.log.Logs;
import io.github.digitalsmile.goldberry.render.Backend;
import io.github.digitalsmile.goldberry.render.BackendException;
import io.github.digitalsmile.goldberry.render.Clipboard;
import io.github.digitalsmile.goldberry.render.backend.sdl3.Sdl3Backend;
import io.github.digitalsmile.goldberry.render.event.BackendEvent;
import io.github.digitalsmile.goldberry.render.event.EventSink;
import io.github.digitalsmile.goldberry.render.model.DisplayScale;
import io.github.digitalsmile.goldberry.render.model.LogicalRect;
import io.github.digitalsmile.goldberry.render.popup.BackendPopup;
import io.github.digitalsmile.goldberry.render.popup.PopupSpec;
import io.github.digitalsmile.goldberry.render.tray.BackendTray;
import io.github.digitalsmile.goldberry.render.tray.TraySpec;
import io.github.digitalsmile.goldberry.render.window.BackendWindow;
import io.github.digitalsmile.goldberry.render.window.WindowSpec;

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

    /// An in-memory clipboard, not [Clipboard#none()].
    ///
    /// A test that copies and pastes should be testing the widget's editing
    /// model, and against a clipboard that accepts nothing every such test would
    /// pass for the wrong reason. This one behaves like a session's: what was
    /// last written is what is read.
    private final StringBuilder clipboardText = new StringBuilder();

    /// The byte half of the same clipboard, which a headless test needs for the
    /// same reason it needs the text half: a paste that could not possibly have
    /// anything to paste tests nothing (ADR-0286).
    ///
    /// Eager where a platform's is lazy — there is no other application to ask,
    /// so the bytes are simply kept. What that cannot model is a *refusal*, and
    /// nothing here pretends to.
    private final Map<String, byte[]> clipboardData = new LinkedHashMap<>();

    private final Clipboard clipboard = new Clipboard() {

        @Override
        public boolean hasText() {
            requireUiThread();
            return !clipboardText.isEmpty();
        }

        @Override
        public String text() {
            requireUiThread();
            return clipboardText.toString();
        }

        @Override
        public boolean text(String text) {
            requireUiThread();
            clipboardText.setLength(0);
            clipboardText.append(text == null ? "" : text);
            return true;
        }

        @Override
        public boolean has(String mime) {
            requireUiThread();
            return clipboardData.containsKey(mime);
        }

        @Override
        public byte[] read(String mime) {
            requireUiThread();
            var bytes = clipboardData.get(mime);
            // A copy, because what a platform hands back is a copy: a test that
            // mutated what it pasted and saw the clipboard change would be
            // learning something about this class rather than about a clipboard.
            return bytes == null ? new byte[0] : bytes.clone();
        }

        @Override
        public java.util.List<String> types() {
            requireUiThread();
            return java.util.List.copyOf(clipboardData.keySet());
        }

        @Override
        public boolean write(Map<String, byte[]> byMime) {
            requireUiThread();
            clipboardData.clear();
            byMime.forEach((mime, bytes) -> clipboardData.put(
                    Objects.requireNonNull(mime, "mime"),
                    Objects.requireNonNull(bytes, "bytes").clone()));
            return true;
        }

        @Override
        public boolean clear() {
            requireUiThread();
            clipboardData.clear();
            clipboardText.setLength(0);
            return true;
        }

        @Override
        public String toString() {
            return "Clipboard[headless, " + clipboardText.length() + " chars, " + clipboardData.size() + " types]";
        }
    };

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

    /// The desktop this backend pretends to have — what
    /// [BackendWindow#workArea()]
    /// answers with.
    ///
    /// A placement policy is only interesting near an edge, and "near an edge"
    /// needs an edge. 1920×1080 with 40 logical pixels reserved at the bottom:
    /// the reservation is there so that a test which confuses the work area with
    /// the display's full size fails, which is the mistake the two rectangles
    /// exist to tell apart.
    private final HeadlessFileDialogs fileDialogs = new HeadlessFileDialogs();

    private LogicalRect workArea = LogicalRect.of(0, 0, 1920, 1040);

    /// The work area every window on this backend reports.
    public LogicalRect workArea() {
        return workArea;
    }

    /// Changes it, for a test that wants a smaller screen or none of it reserved.
    public HeadlessBackend workArea(LogicalRect value) {
        this.workArea = Objects.requireNonNull(value, "workArea");
        return this;
    }

    /// A popup, which this backend has because the SPI's rules need somewhere to
    /// be checked without a display.
    ///
    /// Never empty, unlike a real driver's: a backend with no platform under it
    /// has no driver to lack the capability. The `Optional` is still the SPI's,
    /// and [Sdl3Backend] is where it
    /// is genuinely empty — under SDL's `dummy` driver, which has no popups.
    @Override
    public Optional<BackendPopup> createPopup(BackendWindow owner, PopupSpec spec) {
        requireUiThread();
        requireOpen();
        Objects.requireNonNull(owner, "owner");
        Objects.requireNonNull(spec, "spec");
        if (!(owner instanceof HeadlessWindow parent)) {
            throw new IllegalArgumentException(
                    "a popup's owner must be a window from this backend, and " + owner + " is not");
        }
        if (!parent.isOpen()) {
            throw new IllegalStateException("cannot open a popup on a window that has closed");
        }

        var popup = new HeadlessPopup(this, parent, spec, scale);
        windows.add(popup);
        LOG.debug("created headless {} popup {} at {}", spec.kind(), spec.size(), spec.position());
        return Optional.of(popup);
    }

    /// The trays this backend has open. Never more than an application asked
    /// for; a real desktop imposes no limit either.
    private final List<HeadlessTray> trays = new ArrayList<>();

    /// A tray, which this backend has for the reason it has popups: the SPI's
    /// rules need somewhere to be checked without a platform.
    ///
    /// **Never empty**, unlike a real desktop's — see [HeadlessTray] for why this
    /// is the only place a tray menu's behaviour can be observed at all.
    @Override
    public Optional<BackendTray> createTray(TraySpec spec) {
        requireUiThread();
        requireOpen();
        Objects.requireNonNull(spec, "spec");

        var tray = new HeadlessTray(this, spec);
        trays.add(tray);
        LOG.debug("created headless tray with {} rows, tooltip {}", spec.items().size(), spec.tooltip());
        return Optional.of(tray);
    }

    /// The trays currently up, for a test that wants to find one it did not keep.
    public List<HeadlessTray> trays() {
        requireUiThread();
        return List.copyOf(trays);
    }

    void forget(HeadlessTray tray) {
        trays.remove(tray);
    }

    @Override
    public Clipboard clipboard() {
        return clipboard;
    }

    /// File dialogs a test scripts the answers to.
    ///
    /// Narrowed to [HeadlessFileDialogs] rather than returned as the SPI type,
    /// for the reason [#trays()] is public: a test that cannot reach
    /// `answerWith(...)` has to cast, and the cast would be the only thing in the
    /// test that knows which backend it is running on.
    @Override
    public HeadlessFileDialogs fileDialogs() {
        return fileDialogs;
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
            if (event instanceof BackendEvent.FrameDue frame && frame.window() instanceof HeadlessWindow window) {
                window.frameDelivered();
            }
            // A popup's requested size becomes its actual size here, which is
            // where a window manager's answer would arrive. See HeadlessPopup.
            if (event instanceof BackendEvent.Resized resized && resized.window() instanceof HeadlessPopup popup) {
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
        // And the trays, which belong to the application rather than to a window
        // and would otherwise outlive the backend that made them.
        for (var tray : List.copyOf(trays)) {
            tray.close();
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

    /// Closes every popup belonging to `owner`, as the platform would.
    void closePopupsOf(HeadlessWindow owner) {
        // Copied: closing a popup removes it from the list being walked.
        for (var window : List.copyOf(windows)) {
            if (window instanceof HeadlessPopup popup && popup.owner() == owner) {
                popup.close();
            }
        }
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
