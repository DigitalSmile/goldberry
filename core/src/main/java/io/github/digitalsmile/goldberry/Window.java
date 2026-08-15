package io.github.digitalsmile.goldberry;

import io.github.digitalsmile.goldberry.backend.BackendWindow;
import io.github.digitalsmile.goldberry.backend.DamageRect;
import io.github.digitalsmile.goldberry.backend.DisplayScale;
import io.github.digitalsmile.goldberry.backend.LogicalSize;
import io.github.digitalsmile.goldberry.backend.PhysicalSize;
import io.github.digitalsmile.goldberry.backend.PixelBuffer;
import io.github.digitalsmile.goldberry.backend.PixelFormat;
import io.github.digitalsmile.goldberry.backend.WindowSpec;
import java.util.List;
import java.util.Objects;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;

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

    private final GoldberryRuntime runtime;
    private final BackendWindow window;

    private Consumer<Frame> painter = frame -> {};
    private Consumer<LogicalSize> resizeHandler = size -> {};
    private Consumer<DisplayScale> scaleHandler = scale -> {};
    private BooleanSupplier closeHandler = () -> true;

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
        if (cached == null || !cached.size().equals(size)) {
            cached = PixelBuffer.allocate(size, PixelFormat.BGRA32_PREMULTIPLIED);
        }

        painter.accept(new Frame(cached, window.scale()));
        window.present(cached, List.of(DamageRect.all(size)));
    }

    void handleResize(LogicalSize size) {
        cached = null;
        resizeHandler.accept(size);
        repaint();
    }

    void handleScaleChange(DisplayScale scale) {
        cached = null;
        scaleHandler.accept(scale);
        repaint();
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
