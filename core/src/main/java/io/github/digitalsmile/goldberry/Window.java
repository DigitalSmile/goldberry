package io.github.digitalsmile.goldberry;

import io.github.digitalsmile.goldberry.backend.BackendWindow;
import io.github.digitalsmile.goldberry.backend.DamageRect;
import io.github.digitalsmile.goldberry.backend.DisplayScale;
import io.github.digitalsmile.goldberry.backend.LogicalSize;
import io.github.digitalsmile.goldberry.backend.PhysicalSize;
import io.github.digitalsmile.goldberry.backend.PixelBuffer;
import io.github.digitalsmile.goldberry.backend.PixelFormat;
import io.github.digitalsmile.goldberry.backend.WindowSpec;
import io.github.digitalsmile.goldberry.natives.log.Logs;
import io.github.digitalsmile.goldberry.natives.log.Startup;
import java.util.List;
import java.util.Objects;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import org.slf4j.Logger;

/// A window on the screen.
///
/// This is the front door. Opening one starts the backend and the event loop if
/// they are not already running, so an application never names a backend, builds
/// an event loop, or writes a `switch` over backend events:
///
/// ```java
/// var window = Window.open("Hello", 960, 640);
/// window.onPaint(frame -> frame.fill(0xFF2E3440));
/// Goldberry.run();
/// ```
///
/// Everything below is still there — [io.github.digitalsmile.goldberry.backend]
/// is exported, and an application that wants to drive the SPI directly can. This
/// is the path for the ones that do not.
///
/// Confined to the UI thread, which is the thread that opened the first window.
/// Work that is not instant belongs on [Goldberry#async]; its result comes back
/// here automatically.
public final class Window implements AutoCloseable {

    private static final Logger LOG = Logs.of(Window.class);

    private final GoldberryRuntime runtime;
    private final BackendWindow window;

    private Consumer<Frame> painter = frame -> {};
    private Consumer<LogicalSize> resizeHandler = size -> {};
    private Consumer<DisplayScale> scaleHandler = scale -> {};
    private BooleanSupplier closeHandler = () -> true;

    /// Whether this window has ever reached the screen. The first time is what
    /// the start-up timeline is measuring.
    private boolean everPresented;

    /// Reused between frames while the size holds. Repainting is the common case
    /// and a fresh multi-megabyte buffer per frame is a lot of garbage for
    /// something the backend copies out immediately.
    private PixelBuffer cached;

    private Window(GoldberryRuntime runtime, BackendWindow window) {
        this.runtime = runtime;
        this.window = window;
    }

    /// Opens a window of the given logical size.
    ///
    /// The first call starts the toolkit on the calling thread, which becomes the
    /// UI thread for the process.
    public static Window open(String title, float width, float height) {
        return open(WindowSpec.of(title, LogicalSize.of(width, height)));
    }

    /// Opens a window from a full specification — for the cases that need to say
    /// something about resizability or decorations.
    public static Window open(WindowSpec spec) {
        Objects.requireNonNull(spec, "spec");
        var runtime = GoldberryRuntime.get();
        var backendWindow = runtime.backend().createWindow(spec);
        var window = new Window(runtime, backendWindow);
        runtime.register(backendWindow, window);
        LOG.info("window \"{}\" opened: {} at {} -> {}",
                spec.title(), backendWindow.size(), backendWindow.scale(), backendWindow.physicalSize());
        Startup.mark("window \"" + spec.title() + "\" open");
        window.repaint();
        return window;
    }

    /// Sets what to draw. Called on the UI thread whenever the window needs a
    /// frame, and never for a window that is not visible.
    public Window onPaint(Consumer<Frame> painter) {
        this.painter = Objects.requireNonNull(painter, "painter");
        return repaintAnd();
    }

    /// Called after the window's logical size changes. A repaint is already
    /// scheduled; this is for anything else that has to react.
    public Window onResize(Consumer<LogicalSize> handler) {
        this.resizeHandler = Objects.requireNonNull(handler, "handler");
        return this;
    }

    /// Called when the window moves to a display with a different scale. The
    /// logical size is unchanged — only the resolution it is drawn at.
    public Window onScaleChange(Consumer<DisplayScale> handler) {
        this.scaleHandler = Objects.requireNonNull(handler, "handler");
        return this;
    }

    /// Decides what happens when the user asks to close the window.
    ///
    /// Return `false` to keep it open — which is how an "unsaved changes?" prompt
    /// works. The default closes.
    public Window onCloseRequest(BooleanSupplier handler) {
        this.closeHandler = Objects.requireNonNull(handler, "handler");
        return this;
    }

    /// Asks for a repaint. Coalesced: calling it ten times before the next frame
    /// draws once.
    public void repaint() {
        if (window.isOpen()) {
            window.requestFrame();
        }
    }

    /// The window's size in logical pixels — the units to lay out in.
    public LogicalSize size() {
        return window.size();
    }

    /// The size of the frame buffer behind the window.
    public PhysicalSize pixelSize() {
        return window.physicalSize();
    }

    /// The scale of the display the window is on. Fractional in the ordinary
    /// case: 125% and 150% are what most laptops ship with.
    public DisplayScale scale() {
        return window.scale();
    }

    public String title() {
        return window.title();
    }

    /// Sets the window title.
    public Window title(String title) {
        window.setTitle(title);
        return this;
    }

    public boolean isOpen() {
        return window.isOpen();
    }

    /// Closes the window. The event loop ends when the last one closes.
    @Override
    public void close() {
        if (!window.isOpen()) {
            return;
        }
        LOG.info("window \"{}\" closed", window.title());
        runtime.forget(window);
        window.close();
        cached = null;
    }

    void paint() {
        if (!window.isOpen()) {
            return;
        }
        var size = window.physicalSize();
        if (size.isEmpty()) {
            // Minimized, or mid-resize on a compositor that reports zero. There
            // is nothing to draw into and nothing to show.
            return;
        }
        var traced = LOG.isTraceEnabled();
        var started = traced ? System.nanoTime() : 0L;

        // Paint into the platform's own buffer when it lends one. Otherwise
        // rasterize into ours and let the backend copy -- which is a full frame of
        // memory traffic per frame, so it is the fallback rather than the plan.
        var borrowed = window.acquireFrame();
        PixelBuffer target;
        if (borrowed.isPresent()) {
            target = borrowed.get();
            if (!target.size().equals(size)) {
                // The platform's buffer disagrees with the size just read: a
                // resize landed between the two. Its size is the authoritative
                // one, since it is what will be shown.
                size = target.size();
            }
        } else {
            if (cached == null || !cached.size().equals(size)) {
                LOG.debug("allocating a {} frame buffer", size);
                cached = PixelBuffer.allocate(size, PixelFormat.BGRA32_PREMULTIPLIED);
            }
            target = cached;
        }
        var allocated = traced ? System.nanoTime() : 0L;

        // Ended before the timestamp and before presenting, in that order: a
        // Blend2D context may still have work in flight, and presenting pixels
        // it has not finished with shows a half-drawn frame. The `finally` is
        // what keeps a painter that throws from leaving the context attached to
        // the platform's surface.
        var frame = new Frame(target, window.scale());
        var built = traced ? System.nanoTime() : 0L;
        long drawn;
        try {
            painter.accept(frame);
        } finally {
            drawn = traced ? System.nanoTime() : 0L;
            frame.end();
        }
        var painted = traced ? System.nanoTime() : 0L;

        var frameSize = size;
        try {
            window.present(target, List.of(DamageRect.all(frameSize)));
            if (!everPresented) {
                everPresented = true;
                Startup.mark("first frame presented");
                Startup.summarize();
            }
            if (traced) {
                var done = System.nanoTime();
                // Where a slow frame went. During a resize this is the difference
                // between "the toolkit is slow" and "the platform is".
                // Paint is split three ways because the split is what said where
                // a frame's time actually goes: attaching the context, the
                // painter's own work, and `end` waiting for Blend2D's workers
                // (ADR-0042, ADR-0045).
                LOG.trace("frame {} in {}us: buffer {}, paint {} (begin {}, draw {}, end {}),"
                                + " present {}",
                        frameSize,
                        (done - started) / 1_000,
                        (allocated - started) / 1_000,
                        (painted - allocated) / 1_000,
                        (built - allocated) / 1_000,
                        (drawn - built) / 1_000,
                        (painted - drawn) / 1_000,
                        (done - painted) / 1_000);
            }
        } catch (RuntimeException e) {
            // A window can be resized between the size being read above and the
            // frame reaching the platform -- which during a drag is common, not
            // exotic. The backend refuses a frame that no longer matches its
            // surface, and rightly: a mismatched blit is corruption. But that is
            // a dropped frame, not a failure, and letting it out of here would
            // end the event loop mid-resize.
            var current = window.isOpen() ? window.physicalSize() : frameSize;
            if (!current.equals(frameSize)) {
                LOG.debug("dropped a {} frame: the window became {} while it was painted",
                        frameSize, current);
                repaint();
                return;
            }
            throw e;
        }
    }

    void handleResize(LogicalSize size) {
        LOG.debug("window resized to {} -> {}", size, window.physicalSize());
        // The buffer is NOT dropped here. paint() reallocates when the size no
        // longer matches, which is the same test one step later -- and dropping
        // it eagerly means a multi-megabyte allocation for every resize event a
        // compositor sends, which during a drag is per pointer motion.
        resizeHandler.accept(size);
        repaint();
    }

    void handleScaleChange(DisplayScale scale) {
        LOG.info("window moved to a {} display -> {}", scale, window.physicalSize());
        scaleHandler.accept(scale);
        repaint();
    }

    /// Where pointer events go.
    ///
    /// Null until an application asks for one. A window with no router does the
    /// hit testing work of nothing at all, which is what a window painting with
    /// a plain `onPaint` callback should cost.
    private io.github.digitalsmile.goldberry.input.PointerRouter router;

    /// Routes this window's pointer events through `router`.
    ///
    /// The window feeds it positions; keeping the hit-test snapshot up to date
    /// after each paint is the caller's job, because only the caller knows what
    /// box tree it painted.
    public Window pointerRouter(io.github.digitalsmile.goldberry.input.PointerRouter router) {
        this.router = router;
        if (router != null) {
            // The router decides the shape and knows nothing about the platform;
            // this is the one wire between the two (§7.3).
            router.onCursorChange(window::setCursor);
        }
        return this;
    }

    /// Sets the shape of the pointer over this window (§7.3).
    ///
    /// For an application that decides for itself. A window with a
    /// [#pointerRouter] has this set from the widget under the pointer on every
    /// move, so the two do not mix: whichever spoke last wins, and the router
    /// speaks on the next pointer motion.
    public Window cursor(io.github.digitalsmile.goldberry.backend.Cursor cursor) {
        window.setCursor(Objects.requireNonNull(cursor, "cursor"));
        return this;
    }

    void handlePointerMoved(float x, float y) {
        if (router != null) {
            router.pointerMoved(x, y);
            repaintIfRestyled();
        }
    }

    /// Repaints when input changed something a stylesheet reacts to.
    ///
    /// `:hover` moving is the ordinary case, and it happens on pointer motion —
    /// which arrives whether or not anything asked for a frame. Without this
    /// every application would have to remember to ask after every event, and
    /// the one that forgot would have a window whose buttons never light up.
    ///
    /// Coalesced by [#repaint()], so a drag across a row of buttons costs one
    /// frame per frame rather than one per event. Costs nothing when nothing
    /// changed: `takeStylesDirty` clears on read, so the question it answers is
    /// exactly "did a pseudo-class change since the last time anyone asked".
    private void repaintIfRestyled() {
        if (router.takeStylesDirty()) {
            repaint();
        }
    }

    void handlePointerPressed(float x, float y, int button, int clickCount) {
        if (router != null) {
            router.pointerPressed(x, y, toButton(button), clickCount);
            repaintIfRestyled();
        }
    }

    void handlePointerReleased(float x, float y, int button, int clickCount) {
        if (router != null) {
            router.pointerReleased(x, y, toButton(button), clickCount);
            repaintIfRestyled();
        }
    }

    void handlePointerWheel(float x, float y, float deltaX, float deltaY) {
        if (router != null) {
            router.pointerWheel(x, y, deltaX, deltaY);
        }
    }

    void handlePointerExited() {
        if (router != null) {
            router.pointerExited();
            repaintIfRestyled();
        }
    }

    /// SDL numbers buttons from 1, left first. Anything past the three the
    /// toolkit names is dropped rather than guessed at.
    private static io.github.digitalsmile.goldberry.input.PointerEvent.Button toButton(int sdlButton) {
        return switch (sdlButton) {
            case 1 -> io.github.digitalsmile.goldberry.input.PointerEvent.Button.PRIMARY;
            case 2 -> io.github.digitalsmile.goldberry.input.PointerEvent.Button.MIDDLE;
            case 3 -> io.github.digitalsmile.goldberry.input.PointerEvent.Button.SECONDARY;
            default -> null;
        };
    }

    void handleKeyPressed(int keycode, int modifiers, boolean repeat) {
        if (router != null) {
            router.keyPressed(
                    io.github.digitalsmile.goldberry.input.Key.fromSdl(keycode),
                    io.github.digitalsmile.goldberry.input.Modifiers.fromSdl(modifiers),
                    repeat);
            repaintIfRestyled();
        }
    }

    void handleKeyReleased(int keycode, int modifiers) {
        if (router != null) {
            router.keyReleased(
                    io.github.digitalsmile.goldberry.input.Key.fromSdl(keycode),
                    io.github.digitalsmile.goldberry.input.Modifiers.fromSdl(modifiers));
        }
    }

    void handleTextInput(String text) {
        if (router != null) {
            router.textInput(text);
        }
    }

    void handleCloseRequest() {
        if (closeHandler.getAsBoolean()) {
            close();
        }
    }

    private Window repaintAnd() {
        repaint();
        return this;
    }
}
