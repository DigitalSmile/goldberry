package io.github.digitalsmile.goldberry;

import io.github.digitalsmile.goldberry.backend.Backend;
import io.github.digitalsmile.goldberry.backend.BackendEvent;
import io.github.digitalsmile.goldberry.backend.BackendException;
import io.github.digitalsmile.goldberry.backend.BackendWindow;
import io.github.digitalsmile.goldberry.backend.EventLoop;
import io.github.digitalsmile.goldberry.backend.sdl3.Sdl3Backend;
import io.github.digitalsmile.goldberry.natives.log.Logs;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Objects;
import org.slf4j.Logger;

/// The backend and event loop, owned so applications do not have to be.
///
/// Created on the first [Window#open], on whichever thread makes that call —
/// which becomes the UI thread for the process. That is the same rule the SPI
/// states (ADR-0019); the only difference is that nobody has to write it down in
/// their `main`.
///
/// Package-private. [Goldberry] and [Window] are the public surface; this is the
/// wiring behind them.
final class GoldberryRuntime {

    private static final Logger LOG = Logs.of(GoldberryRuntime.class);

    private static GoldberryRuntime instance;

    private final Backend backend;
    private final EventLoop loop;
    private final Map<BackendWindow, Window> windows = new IdentityHashMap<>();

    private GoldberryRuntime(Backend backend) {
        this.backend = backend;
        this.loop = new EventLoop(backend);
    }

    /// The runtime, starting the desktop backend on first use.
    static synchronized GoldberryRuntime get() {
        if (instance == null) {
            LOG.debug("starting the Goldberry runtime on {}", Thread.currentThread().getName());
            instance = new GoldberryRuntime(new Sdl3Backend());
        }
        return instance;
    }

    /// Installs a backend before anything starts one.
    ///
    /// How tests run the whole front door against `headless`, with no display and
    /// no native library. Not public: an application choosing its own backend is
    /// a real use case, but not one with a caller yet, and a setter that must be
    /// called before an implicit initialization is a bad shape to publish.
    static synchronized void install(Backend backend) {
        if (instance != null) {
            throw new IllegalStateException("the Goldberry runtime is already started");
        }
        instance = new GoldberryRuntime(Objects.requireNonNull(backend, "backend"));
    }

    /// Whether a runtime exists yet. Lets [Goldberry#run()] refuse politely
    /// rather than starting a backend nobody asked for.
    static synchronized boolean isStarted() {
        return instance != null;
    }

    static synchronized void shutdown() {
        if (instance == null) {
            return;
        }
        var runtime = instance;
        instance = null;
        LOG.debug("shutting the Goldberry runtime down");
        runtime.loop.close();
        runtime.backend.close();
        runtime.windows.clear();
    }

    Backend backend() {
        return backend;
    }

    EventLoop loop() {
        return loop;
    }

    void register(BackendWindow backendWindow, Window window) {
        windows.put(backendWindow, window);
    }

    void forget(BackendWindow backendWindow) {
        windows.remove(backendWindow);
    }

    /// Runs until every window has closed.
    void run() {
        loop.requireNotRunning();
        loop.run(this::dispatch);
    }

    /// Routes one backend event to the window it belongs to.
    ///
    /// The exhaustive switch is the reason [BackendEvent] is sealed: when pointer
    /// and keyboard events arrive, this stops compiling until it says what it
    /// does with them.
    private void dispatch(BackendEvent event) {
        var window = windows.get(event.window());
        if (window == null) {
            // An event for a window already closed on the Java side. The backend
            // had not caught up; there is nothing left to tell.
            return;
        }
        switch (event) {
            case BackendEvent.FrameDue ignored -> window.paint();
            case BackendEvent.Exposed ignored -> window.repaint();
            case BackendEvent.Resized resized -> window.handleResize(resized.size());
            case BackendEvent.ScaleChanged rescaled -> window.handleScaleChange(rescaled.scale());
            case BackendEvent.CloseRequested ignored -> window.handleCloseRequest();
        }
    }

    static BackendException notStarted() {
        return new BackendException(
                "no window has been opened, so there is nothing to run."
                        + " Call Window.open(...) first.");
    }
}
