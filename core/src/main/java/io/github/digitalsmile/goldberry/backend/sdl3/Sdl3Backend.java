package io.github.digitalsmile.goldberry.backend.sdl3;

import io.github.digitalsmile.goldberry.backend.Backend;
import io.github.digitalsmile.goldberry.backend.BackendEvent;
import io.github.digitalsmile.goldberry.backend.BackendException;
import io.github.digitalsmile.goldberry.backend.BackendWindow;
import io.github.digitalsmile.goldberry.backend.EventSink;
import io.github.digitalsmile.goldberry.backend.WindowSpec;
import io.github.digitalsmile.goldberry.natives.sdl.Sdl;
import io.github.digitalsmile.goldberry.natives.sdl.SdlCursors;
import io.github.digitalsmile.goldberry.natives.sdl.SdlEventBuffer;
import io.github.digitalsmile.goldberry.natives.sdl.SdlEventType;
import io.github.digitalsmile.goldberry.natives.sdl.SdlEventWatch;
import io.github.digitalsmile.goldberry.natives.sdl.SdlException;
import io.github.digitalsmile.goldberry.natives.sdl.SdlSubsystem;
import io.github.digitalsmile.goldberry.natives.sdl.SdlSystemCursor;
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

    /// Turns the frame loop's pacing off. See [#pacePresentToTheDisplay()].
    public static final String VSYNC_PROPERTY = "goldberry.backend.vsync";

    /// The macOS `java` launcher sets `JAVA_STARTED_ON_FIRST_THREAD_<pid>=1` in
    /// the environment when it is given `-XstartOnFirstThread`. It is the only
    /// way to ask, from Java, whether `main` is running on the process's first
    /// thread — and the answer decides whether AppKit can start at all.
    private static final String FIRST_THREAD_ENV = "JAVA_STARTED_ON_FIRST_THREAD_";

    /// The flag whose absence is, on macOS, by far the most likely reason
    /// `SDL_Init` reports no video device.
    private static final String FIRST_THREAD_FLAG = "-XstartOnFirstThread";

    private final SdlVideo video = SdlVideo.get();
    private final Thread uiThread = Thread.currentThread();
    private final Map<Integer, Sdl3Window> windowsById = new LinkedHashMap<>();
    private final SdlEventBuffer eventBuffer = new SdlEventBuffer();
    private final FramePacer pacer = FramePacer.fromProperties();

    /// The system cursors, created on first use.
    ///
    /// Lazy and optional, for the reason `SdlVideo.optionalDowncall` is: a
    /// `libgoldberry` built before the cursor symbols were exported would
    /// otherwise stop opening windows at all, to enable a nicety. An application
    /// that never sets a cursor never creates one either.
    private SdlCursors cursors;
    private boolean cursorsUnavailable;

    /// Draws while the platform is holding the thread. See [#drawDuringModalLoop]
    /// and ADR-0060. Null when `libgoldberry` does not export the watch calls.
    private SdlEventWatch resizeWatch;

    /// Where a watched event goes — non-null only for the duration of a
    /// [#pumpEvents] call, which is the only time there is anywhere to send one.
    private EventSink activeSink;

    /// Guards against the watch re-entering itself. Painting pushes events —
    /// [Sdl3Window#requestFrame] wakes the loop — and every push runs the watch
    /// again, on this thread, from inside the paint it would restart.
    private boolean inWatch;

    private boolean closed;

    /// Initializes SDL's video subsystem.
    ///
    /// Video implies events, so this is the only initialization the backend needs.
    ///
    /// @throws BackendException if SDL cannot start — no display, no driver
    public Sdl3Backend() {
        try {
            selectVideoDriver();
            pacePresentToTheDisplay();
            Startup.time("SDL video subsystem up",
                    () -> Sdl.get().initialize(EnumSet.of(SdlSubsystem.VIDEO)));
            LOG.info("sdl3 backend started on SDL {}, video driver {}",
                    Sdl.get().version(), Sdl.get().videoDriver());
            if (pacer.isPacing()) {
                LOG.info("frame loop paced to one frame per {}", pacer.interval());
            }
            installResizeWatch();
        } catch (SdlException e) {
            eventBuffer.close();
            throw new BackendException(videoFailureMessage(), e);
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

    /// Asks SDL to hold each `present` until the display is ready for it.
    ///
    /// Goldberry creates no renderer, so at first glance this hint belongs to
    /// somebody else. It does not. Where the video driver implements no window
    /// surface — Wayland — `SDL_GetWindowSurface` falls back to a hidden
    /// `SDL_Renderer` and every present ends in its `SDL_RenderPresent`
    /// (ADR-0046). Left alone, that renderer does not wait for the display, and
    /// the frame loop runs as fast as the swapchain will take frames: measured
    /// at ~105 fps into a 59.96 Hz panel, so two frames in five were rasterized,
    /// uploaded, and thrown away.
    ///
    /// Painting a frame nobody will see costs its paint *and* its present, which
    /// is the largest single saving available in the loop — larger than anything
    /// left inside either half.
    ///
    /// Set before `SDL_Init`, and well before the first [Sdl3Window#acquireFrame],
    /// which is when SDL actually builds the renderer.
    ///
    /// `-Dgoldberry.backend.vsync=false` turns it off, for measuring the
    /// unthrottled loop or for a benchmark that wants every frame it can get.
    private static void pacePresentToTheDisplay() {
        if (!Boolean.parseBoolean(System.getProperty(VSYNC_PROPERTY, "true"))) {
            LOG.debug("leaving present unpaced ({}=false)", VSYNC_PROPERTY);
            return;
        }
        if (Sdl.get().setHint(Sdl.RENDER_VSYNC_HINT, "1")) {
            LOG.debug("pacing present to the display ({}=1)", Sdl.RENDER_VSYNC_HINT);
        } else {
            // Not fatal: an unpaced loop draws the same pixels, just more of them
            // than anyone will look at.
            LOG.warn("SDL refused {}; the frame loop will not be paced to the display",
                    Sdl.RENDER_VSYNC_HINT);
        }
    }

    private static void applyVideoDriver(String drivers, String because) {
        if (Sdl.get().setHint(Sdl.VIDEO_DRIVER_HINT, drivers)) {
            LOG.debug("asking SDL for video driver {} ({})", drivers, because);
        } else {
            LOG.warn("SDL refused the video driver hint {}={}", Sdl.VIDEO_DRIVER_HINT, drivers);
        }
    }

    /// Whether this JVM is one where AppKit cannot start.
    ///
    /// macOS requires AppKit to be driven from the process's first thread, and the
    /// `java` launcher runs `main` on a secondary thread unless it is given
    /// `-XstartOnFirstThread`. SDL's Cocoa driver therefore refuses to create a
    /// device, `SDL_Init` finds no other driver, and the error it reports is the
    /// thoroughly unhelpful "No available video device" — which names neither the
    /// thread nor the flag (ADR-0039).
    ///
    /// Taken as a hint rather than a precondition. The environment variable is set
    /// by the launcher, so a JVM embedded through `JNI_CreateJavaVM` on the real
    /// main thread would not have it and would still work; failing up front on the
    /// strength of that would be wrong. Everything here is therefore phrased as
    /// "this is probably why", and only ever after SDL has actually said no.
    ///
    /// @param osName the value of `os.name`
    /// @param firstThreadEnv the value of `JAVA_STARTED_ON_FIRST_THREAD_<pid>`, or null
    /// @return true if the missing flag is worth mentioning
    static boolean firstThreadFlagLikelyMissing(String osName, String firstThreadEnv) {
        var macOs = osName != null
                && (osName.toLowerCase(Locale.ROOT).contains("mac")
                        || osName.toLowerCase(Locale.ROOT).contains("darwin"));
        return macOs && !"1".equals(firstThreadEnv);
    }

    private static boolean firstThreadFlagLikelyMissing() {
        return firstThreadFlagLikelyMissing(
                System.getProperty("os.name", ""),
                System.getenv(FIRST_THREAD_ENV + ProcessHandle.current().pid()));
    }

    /// The message for a video subsystem that would not start, with the macOS
    /// first-thread explanation attached when it applies.
    ///
    /// @param flagMissing whether to append the `-XstartOnFirstThread` guidance
    /// @return the exception message
    static String videoFailureMessage(boolean flagMissing) {
        var base = "SDL could not initialize its video subsystem";
        if (!flagMissing) {
            return base;
        }
        return base + """
                .
                On macOS the UI toolkit has to run on the process's first thread, and \
                the java launcher does not put main there by default — so SDL finds no \
                usable video driver and reports "No available video device".
                Add the JVM flag: %s
                For the showcase, ./gradlew run already passes it.""".formatted(FIRST_THREAD_FLAG);
    }

    private static String videoFailureMessage() {
        return videoFailureMessage(firstThreadFlagLikelyMissing());
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

        adoptDisplayRate();

        // Shortened when a frame is being held back, so the wait ends when that
        // frame comes due rather than at the loop's one-second heartbeat.
        var wait = pacer.capWait(timeout, anyFramePending(), System.nanoTime());

        // One blocking wait, then drain whatever else is queued. Waiting per
        // event would sleep between two events that arrived together.
        var millis = (int) Math.min(wait.toMillis(), Integer.MAX_VALUE);

        // Published for the event watch, which runs *inside* the calls below --
        // including the ones the platform makes for itself during a resize drag,
        // when they do not return for as long as the drag lasts (ADR-0060).
        activeSink = sink;
        try {
            var hasEvent = wait.isZero() || millis == 0
                    ? video.pollEvent(eventBuffer)
                    : video.waitEvent(eventBuffer, millis);
            while (hasEvent) {
                translate(eventBuffer.type(), eventBuffer.windowId(), translated);
                hasEvent = video.pollEvent(eventBuffer);
            }

            for (var event : translated) {
                sink.accept(event);
            }

            // Frames AFTER the events that caused them.
            //
            // Collecting requests before dispatching would miss every repaint
            // asked for by a handler -- which is most of them: a resize, an
            // expose and a scale change all end in repaint(). Those frames would
            // then wait for the next pump, and during an interactive resize that
            // reads as the window lagging a step behind the pointer with black
            // where it has not caught up.
            //
            // Requests made while handling a FrameDue are deliberately left for
            // the next pump: draining until empty here would let a
            // self-scheduling animation hold the loop and starve input.
            return translated.size() + emitDueFrames(sink);
        } finally {
            activeSink = null;
        }
    }

    /// Hands over a frame for every window that asked for one and may have it now.
    ///
    /// Held back when the display cannot have wanted a frame yet. The request
    /// stays pending rather than being dropped, so the next pump — woken by the
    /// shortened wait in [#pumpEvents] — emits it.
    ///
    /// Called from the pump, and from the event watch during a resize drag, where
    /// it is the only thing that draws. The pacer applies in both: a drag that
    /// outran the display would be spending frames nobody sees (ADR-0047), and a
    /// frame held back during a drag is emitted by the next resize event, of which
    /// there are many.
    ///
    /// @return how many frames were emitted
    private int emitDueFrames(EventSink sink) {
        var now = System.nanoTime();
        if (!pacer.isDue(now)) {
            return 0;
        }
        var frames = new ArrayList<BackendEvent>();
        for (var window : windowsById.values()) {
            if (window.takeFrameRequest()) {
                frames.add(new BackendEvent.FrameDue(window));
            }
        }
        if (frames.isEmpty()) {
            return 0;
        }
        pacer.frameEmitted(now);
        for (var event : frames) {
            sink.accept(event);
        }
        return frames.size();
    }

    /// Installs the watch that keeps frames coming during a resize drag.
    ///
    /// Optional, for the same reason the cursors are: a `libgoldberry` built
    /// before these symbols were exported should lose live resize, not the
    /// ability to open a window.
    private void installResizeWatch() {
        try {
            resizeWatch = SdlEventWatch.install(this::drawDuringModalLoop);
        } catch (UnsatisfiedLinkError | SdlException e) {
            LOG.debug("no event watch, so a resize drag on Windows or macOS will not"
                    + " redraw until it ends", e);
        }
    }

    /// Draws while the platform is holding the thread.
    ///
    /// Windows and macOS run a modal loop for the duration of a resize gesture:
    /// SDL keeps pumping events inside it, but does not return from the pump, so
    /// the frame loop does not iterate and the window shows stale content until
    /// the drag ends (ADR-0024). SDL calls an event watch from inside that pump —
    /// so this is the one place a frame can be produced while it runs.
    ///
    /// Everything here is a guard except the last four lines:
    ///
    /// - **off the UI thread** — [#wakeup()] pushes from background threads, and
    ///   every push runs the watch on the pushing thread;
    /// - **no active sink** — an event pushed between pumps has nowhere to go, and
    ///   the queue will deliver it in the ordinary way anyway;
    /// - **already inside the watch** — painting asks for the next frame, which
    ///   pushes a wakeup, which runs the watch again;
    /// - **not a window event** — input is handled by the pump, which is not
    ///   starved: what a modal loop withholds is frames.
    ///
    /// The duplicate this creates is expected. The queue still gets the event, and
    /// the pump still translates it when the drag ends; [Sdl3Window#resizedTo] is
    /// what keeps that from being a second layout pass.
    private void drawDuringModalLoop(SdlEventBuffer event) {
        if (closed || inWatch || activeSink == null || Thread.currentThread() != uiThread) {
            return;
        }
        var type = event.type();
        if (type != SdlEventType.WINDOW_RESIZED.value()
                && type != SdlEventType.WINDOW_EXPOSED.value()) {
            return;
        }

        inWatch = true;
        try {
            var translated = new ArrayList<BackendEvent>(1);
            translate(type, event.windowId(), translated);
            for (var backendEvent : translated) {
                activeSink.accept(backendEvent);
            }
            emitDueFrames(activeSink);
        } catch (RuntimeException e) {
            // The alternative is an exception unwinding into the platform's own
            // resize loop, which loses the window rather than a frame.
            LOG.warn("drawing during a resize failed", e);
        } finally {
            inWatch = false;
        }
    }

    /// Paces the loop to the fastest display any open window is on.
    ///
    /// The fastest rather than the first: two windows on a 60 Hz and a 144 Hz
    /// monitor share one loop, and pacing that loop to 60 would starve the window
    /// on the faster one. Overshooting costs the slower window a discarded frame;
    /// undershooting costs the faster one a missed refresh, and the second is
    /// what the user sees.
    ///
    /// Each window caches its own rate, so this is a field read per pump rather
    /// than a native call. Does nothing when `goldberry.frame.rate` was set.
    private void adoptDisplayRate() {
        if (pacer.isExplicit()) {
            return;
        }
        var fastest = 0f;
        for (var window : windowsById.values()) {
            fastest = Math.max(fastest, window.refreshRate());
        }
        if (pacer.useDisplayRate(fastest)) {
            LOG.info("pacing the frame loop to {} Hz, one frame per {}", fastest, pacer.interval());
        }
    }

    /// Whether any window is waiting for a frame. Only asked when pacing, and
    /// only to decide how long the next wait may be.
    private boolean anyFramePending() {
        for (var window : windowsById.values()) {
            if (window.isFramePending()) {
                return true;
            }
        }
        return false;
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
            // The sizes are read off the window rather than out of the event, and
            // reported only when they are news: since ADR-0060 the same resize
            // arrives twice by design -- once in the event watch while the drag is
            // still running, once from the queue when it ends.
            var logical = window.size();
            var physical = window.physicalSize();
            if (window.resizedTo(logical, physical)) {
                out.add(new BackendEvent.Resized(window, logical, physical));
            }
        } else if (type == SdlEventType.WINDOW_PIXEL_SIZE_CHANGED.value()) {
            // The backing store moved without the logical size necessarily
            // moving. No event of its own: the resize or scale change that
            // caused it carries the news.
        } else if (type == SdlEventType.KEY_DOWN.value()) {
            out.add(new BackendEvent.KeyPressed(window, eventBuffer.keycode(),
                    eventBuffer.keyModifiers(), eventBuffer.isRepeat()));
        } else if (type == SdlEventType.KEY_UP.value()) {
            out.add(new BackendEvent.KeyReleased(window, eventBuffer.keycode(),
                    eventBuffer.keyModifiers()));
        } else if (type == SdlEventType.TEXT_INPUT.value()) {
            // Copied out of SDL's memory here, while the event is still the
            // current one -- the pointer dies at the next pump.
            out.add(new BackendEvent.TextInput(window, eventBuffer.committedText()));
        } else if (type == SdlEventType.MOUSE_MOTION.value()) {
            out.add(new BackendEvent.PointerMoved(window, eventBuffer.pointerX(), eventBuffer.pointerY()));
        } else if (type == SdlEventType.MOUSE_BUTTON_DOWN.value()) {
            out.add(new BackendEvent.PointerPressed(window, eventBuffer.pointerX(), eventBuffer.pointerY(),
                    eventBuffer.mouseButton(), eventBuffer.clickCount()));
        } else if (type == SdlEventType.MOUSE_BUTTON_UP.value()) {
            out.add(new BackendEvent.PointerReleased(window, eventBuffer.pointerX(), eventBuffer.pointerY(),
                    eventBuffer.mouseButton(), eventBuffer.clickCount()));
        } else if (type == SdlEventType.MOUSE_WHEEL.value()) {
            // The buffer has already undone SDL's "natural scrolling" inversion.
            // What is left is the sign convention: SDL's y is positive *away from
            // the user*, and the SPI's is positive *down the document*, which is
            // CSS's and every scroll view's. One negation, at the boundary, once.
            out.add(new BackendEvent.PointerWheel(window,
                    eventBuffer.wheelPointerX(), eventBuffer.wheelPointerY(),
                    eventBuffer.wheelX(), -eventBuffer.wheelY()));
        } else if (type == SdlEventType.WINDOW_DISPLAY_SCALE_CHANGED.value()) {
            // Usually the window moving to another monitor, which is also the one
            // case where the cached refresh rate can be wrong -- and wrong for the
            // life of the window if it is not dropped here.
            window.forgetRefreshRate();
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
        // Before the windows: the watch is a native callback into this object, and
        // SDL must stop calling it while there is still something to call.
        if (resizeWatch != null) {
            resizeWatch.close();
            resizeWatch = null;
        }
        for (var window : List.copyOf(windowsById.values())) {
            window.close();
        }
        windowsById.clear();
        if (cursors != null) {
            cursors.close();
            cursors = null;
        }
        eventBuffer.close();
        Sdl.get().quit();
    }

    SdlVideo video() {
        return video;
    }

    /// Shows a system cursor.
    ///
    /// SDL's cursor is process-global — one pointer, one shape — so this lives on
    /// the backend rather than on a window, and the window that asks is by
    /// definition the one the pointer is in.
    void setCursor(SdlSystemCursor shape) {
        if (cursorsUnavailable) {
            return;
        }
        if (cursors == null) {
            try {
                cursors = new SdlCursors();
            } catch (UnsatisfiedLinkError e) {
                cursorsUnavailable = true;
                LOG.debug("libgoldberry exports no cursor calls; the pointer keeps its"
                        + " default shape", e);
                return;
            }
        }
        cursors.set(shape);
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
