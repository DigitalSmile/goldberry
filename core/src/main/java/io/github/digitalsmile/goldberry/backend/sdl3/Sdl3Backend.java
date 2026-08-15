package io.github.digitalsmile.goldberry.backend.sdl3;

import io.github.digitalsmile.goldberry.backend.Backend;
import io.github.digitalsmile.goldberry.backend.BackendEvent;
import io.github.digitalsmile.goldberry.backend.BackendException;
import io.github.digitalsmile.goldberry.backend.BackendWindow;
import io.github.digitalsmile.goldberry.backend.EventSink;
import io.github.digitalsmile.goldberry.backend.WindowSpec;
import io.github.digitalsmile.goldberry.natives.sdl.Sdl;
import io.github.digitalsmile.goldberry.natives.sdl.SdlEventBuffer;
import io.github.digitalsmile.goldberry.natives.sdl.SdlEventType;
import io.github.digitalsmile.goldberry.natives.sdl.SdlException;
import io.github.digitalsmile.goldberry.natives.sdl.SdlSubsystem;
import io.github.digitalsmile.goldberry.natives.sdl.SdlVideo;
import io.github.digitalsmile.goldberry.natives.sdl.SdlWindowFlag;
import io.github.digitalsmile.goldberry.natives.log.Logs;
import io.github.digitalsmile.goldberry.natives.log.Startup;
import java.time.Duration;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.slf4j.Logger;

/// The desktop backend (ADR-0003).
///
/// SDL3 owns the window, the event queue and the presentation surface on all
/// three desktop platforms. This class is the translation layer: SDL's event
/// numbers become [BackendEvent]s, SDL's failures become [BackendException]s, and
/// SDL's window handles never leave.
///
/// Confined to the UI thread, with [#wakeup()] the sole exception — SDL's event
/// queue is internally locked, so pushing to it is the one thing another thread
/// may do.
public final class Sdl3Backend implements Backend {

    private static final Logger LOG = Logs.of(Sdl3Backend.class);

    /// Overrides the video driver choice. See [#selectVideoDriver()].
    public static final String VIDEO_DRIVER_PROPERTY = "goldberry.backend.videoDriver";

    /// What Goldberry asks for on a Linux Wayland session: a preference, with
    /// X11 behind it, resolved inside SDL.
    private static final String PREFERRED_LINUX_DRIVERS = "wayland,x11";

    private final SdlVideo video = SdlVideo.get();
    private final Thread uiThread = Thread.currentThread();
    private final Map<Integer, Sdl3Window> windowsById = new LinkedHashMap<>();
    private final SdlEventBuffer eventBuffer = new SdlEventBuffer();

    private boolean closed;

    /// Initializes SDL's video subsystem.
    ///
    /// Video implies events, so this is the only initialization the backend needs.
    ///
    /// @throws BackendException if SDL cannot start — no display, no driver
    public Sdl3Backend() {
        try {
            selectVideoDriver();
            Startup.time("SDL video subsystem up",
                    () -> Sdl.get().initialize(EnumSet.of(SdlSubsystem.VIDEO)));
            LOG.info("sdl3 backend started on SDL {}, video driver {}",
                    Sdl.get().version(), Sdl.get().videoDriver());
        } catch (SdlException e) {
            eventBuffer.close();
            throw new BackendException("SDL could not initialize its video subsystem", e);
        } catch (UnsatisfiedLinkError e) {
            eventBuffer.close();
            throw new BackendException(
                    "libgoldberry is not available, so the sdl3 backend cannot start."
                            + " Add the goldberry-natives artifact for this platform,"
                            + " or set -Dgoldberry.native.library=<path>.",
                    e);
        }
    }

    /// Chooses the video driver.
    ///
    /// SDL picks for itself and its choice is informed: on Linux it prefers
    /// Wayland only when the compositor advertises `wp_fifo_manager_v1`, and falls
    /// back to X11 otherwise, because without that protocol it judges its own
    /// Wayland presentation to have no reliable frame pacing. GNOME's Mutter does
    /// not advertise it, so on the most common Linux desktop SDL chooses XWayland.
    ///
    /// Goldberry asks for Wayland first anyway, because what SDL is protecting
    /// against is less bad than what it falls back to: an XWayland window resizes
    /// visibly worse than a native one. Measured on GNOME, the Wayland path
    /// reports *higher* per-frame numbers and looks better, because the extra time
    /// is the compositor pacing the client rather than work (ADR-0027).
    ///
    /// The hint takes a comma-separated list and SDL tries each in turn, so
    /// `wayland,x11` is a preference and not a demand — a machine with no Wayland
    /// gets X11 exactly as before, inside SDL, with no fallback logic here.
    ///
    /// Three things override it, in order: `-Dgoldberry.backend.videoDriver`, an
    /// `SDL_VIDEO_DRIVER` already in the environment, and not being on Linux.
    private static void selectVideoDriver() {
        var requested = System.getProperty(VIDEO_DRIVER_PROPERTY);
        if (requested != null && !requested.isBlank()) {
            applyVideoDriver(requested, "asked for by " + VIDEO_DRIVER_PROPERTY);
            return;
        }

        // SDL reads this hint from the environment too. Setting it here would
        // silently beat what the user put in their shell.
        if (System.getenv(Sdl.VIDEO_DRIVER_HINT) != null || System.getenv("SDL_VIDEODRIVER") != null) {
            LOG.debug("leaving the video driver to the environment");
            return;
        }

        var linux = System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("linux");
        var waylandSession = System.getenv("WAYLAND_DISPLAY");
        if (linux && waylandSession != null && !waylandSession.isBlank()) {
            applyVideoDriver(PREFERRED_LINUX_DRIVERS, "a Wayland session is running");
        }
    }

    private static void applyVideoDriver(String drivers, String because) {
        if (Sdl.get().setHint(Sdl.VIDEO_DRIVER_HINT, drivers)) {
            LOG.debug("asking SDL for video driver {} ({})", drivers, because);
        } else {
            LOG.warn("SDL refused the video driver hint {}={}", Sdl.VIDEO_DRIVER_HINT, drivers);
        }
    }

    @Override
    public String name() {
        return "sdl3";
    }

    /// The version of SDL linked into `libgoldberry`, for diagnostics.
    public String sdlVersion() {
        return Sdl.get().version().toString();
    }

    @Override
    public BackendWindow createWindow(WindowSpec spec) {
        requireUiThread();
        requireOpen();
        Objects.requireNonNull(spec, "spec");

        var flags = EnumSet.of(SdlWindowFlag.HIGH_PIXEL_DENSITY, SdlWindowFlag.HIDDEN);
        if (spec.resizable()) {
            flags.add(SdlWindowFlag.RESIZABLE);
        }
        if (!spec.decorated()) {
            flags.add(SdlWindowFlag.BORDERLESS);
        }

        // SDL takes window sizes in logical pixels, which is what WindowSpec
        // carries -- no conversion, and deliberately none: converting here would
        // ask for a physical size and get a window that is scale times too big.
        var handle = video.createWindow(
                spec.title(),
                Math.round(spec.size().width()),
                Math.round(spec.size().height()),
                flags);

        var window = new Sdl3Window(this, handle, spec.title());
        windowsById.put(handle.id(), window);
        Startup.mark("SDL window " + handle.id() + " created");
        LOG.debug("created SDL window {} \"{}\" {} flags={}",
                handle.id(), spec.title(), spec.size(), flags);
        return window;
    }

    @Override
    public List<BackendWindow> windows() {
        requireUiThread();
        return List.copyOf(windowsById.values());
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

        var translated = new ArrayList<BackendEvent>();

        // One blocking wait, then drain whatever else is queued. Waiting per
        // event would sleep between two events that arrived together.
        var millis = (int) Math.min(timeout.toMillis(), Integer.MAX_VALUE);
        var hasEvent = timeout.isZero() ? video.pollEvent(eventBuffer) : video.waitEvent(eventBuffer, millis);
        while (hasEvent) {
            translate(eventBuffer.type(), eventBuffer.windowId(), translated);
            hasEvent = video.pollEvent(eventBuffer);
        }

        for (var event : translated) {
            sink.accept(event);
        }

        // Frames AFTER the events that caused them.
        //
        // Collecting requests before dispatching would miss every repaint asked
        // for by a handler -- which is most of them: a resize, an expose and a
        // scale change all end in repaint(). Those frames would then wait for the
        // next pump, and during an interactive resize that reads as the window
        // lagging a step behind the pointer with black where it has not caught
        // up.
        //
        // Requests made while handling a FrameDue below are deliberately left for
        // the next pump: draining until empty here would let a self-scheduling
        // animation hold the loop and starve input.
        var frames = new ArrayList<BackendEvent>();
        for (var window : windowsById.values()) {
            if (window.takeFrameRequest()) {
                frames.add(new BackendEvent.FrameDue(window));
            }
        }
        for (var event : frames) {
            sink.accept(event);
        }

        return translated.size() + frames.size();
    }

    private void translate(int type, int windowId, List<BackendEvent> out) {
        if (type == SdlEventType.QUIT.value()) {
            // No window of its own: the session is ending, so every window is
            // being asked to close.
            //
            // Deduplicated against this batch. SDL posts QUIT once the last
            // window has been asked to close, so a plain window close arrives as
            // CLOSE_REQUESTED *and* QUIT -- and an application that prompts
            // "discard unsaved changes?" would ask twice for one click.
            for (var window : windowsById.values()) {
                if (!alreadyAskedToClose(out, window)) {
                    out.add(new BackendEvent.CloseRequested(window));
                }
            }
            return;
        }

        var window = windowsById.get(windowId);
        if (window == null) {
            // An event for a window we have already destroyed, or one of the many
            // SDL events Goldberry does not handle yet. Both are skipped by
            // number, which is why those numbers are verified against C.
            return;
        }

        if (type == SdlEventType.WINDOW_CLOSE_REQUESTED.value()) {
            out.add(new BackendEvent.CloseRequested(window));
        } else if (type == SdlEventType.WINDOW_EXPOSED.value()) {
            out.add(new BackendEvent.Exposed(window));
        } else if (type == SdlEventType.WINDOW_RESIZED.value()) {
            out.add(new BackendEvent.Resized(window, window.size(), window.physicalSize()));
        } else if (type == SdlEventType.WINDOW_PIXEL_SIZE_CHANGED.value()) {
            // The backing store moved without the logical size necessarily
            // moving. No event of its own: the resize or scale change that
            // caused it carries the news.
        } else if (type == SdlEventType.WINDOW_DISPLAY_SCALE_CHANGED.value()) {
            out.add(new BackendEvent.ScaleChanged(window, window.scale(), window.physicalSize()));
        }

        // Nothing here destroys the window surface. SDL invalidates it itself on
        // resize and SDL_GetWindowSurface -- which every present calls -- hands
        // back one of the right size. Destroying it eagerly meant a full surface
        // reallocation for every resize event a compositor sends, which during a
        // drag is per pointer motion, and left the window with no buffer to show
        // in between (ADR-0024).
    }

    private static boolean alreadyAskedToClose(List<BackendEvent> out, Sdl3Window window) {
        for (var event : out) {
            if (event instanceof BackendEvent.CloseRequested request && request.window() == window) {
                return true;
            }
        }
        return false;
    }

    @Override
    public void wakeup() {
        // No requireUiThread: SDL's event queue takes its own lock, and this is
        // the one call the SPI promises is safe from anywhere.
        if (!closed) {
            video.pushWakeup();
        }
    }

    @Override
    public void close() {
        if (closed) {
            return;
        }
        requireUiThread();
        closed = true;
        LOG.debug("closing the sdl3 backend and its {} window(s)", windowsById.size());
        for (var window : List.copyOf(windowsById.values())) {
            window.close();
        }
        windowsById.clear();
        eventBuffer.close();
        Sdl.get().quit();
    }

    SdlVideo video() {
        return video;
    }

    void forget(Sdl3Window window) {
        windowsById.remove(window.handleId());
    }

    void requireUiThread() {
        if (Thread.currentThread() != uiThread) {
            throw new BackendException(
                    "the sdl3 backend was called from " + Thread.currentThread().getName()
                            + " but belongs to " + uiThread.getName()
                            + ". Every call except wakeup() must be on the UI thread.");
        }
    }

    private void requireOpen() {
        if (closed) {
            throw new BackendException("the sdl3 backend is closed");
        }
    }
}
