package io.github.digitalsmile.goldberry.natives.sdl;

import io.github.digitalsmile.goldberry.natives.Downcalls;
import io.github.digitalsmile.goldberry.natives.NativeLibrary;
import io.github.digitalsmile.goldberry.natives.layout.Layouts;
import io.github.digitalsmile.goldberry.natives.log.Logs;
import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.SymbolLookup;
import java.lang.foreign.ValueLayout;
import java.nio.ByteBuffer;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import org.slf4j.Logger;

/// SDL3's windowing, event and CPU presentation calls.
///
/// Every method here must be called on the UI thread, with one exception:
/// [#pushWakeup()] is safe from anywhere, because SDL's event queue is
/// internally locked and pushing to it is the sanctioned way to reach the event
/// loop from another thread.
///
/// The present path lives here rather than above the boundary. Copying rows into
/// an `SDL_Surface` means touching the surface's `pixels` pointer, and §3.1 keeps
/// that inside this module — so `:core` hands over a [ByteBuffer] and this class
/// does the blit.
public final class SdlVideo {

    private static final Logger LOG = Logs.of(SdlVideo.class);

    private static final long SURFACE_FORMAT = Layouts.SDL_SURFACE.offsetOf("format");
    private static final long SURFACE_WIDTH = Layouts.SDL_SURFACE.offsetOf("w");
    private static final long SURFACE_HEIGHT = Layouts.SDL_SURFACE.offsetOf("h");
    private static final long SURFACE_PITCH = Layouts.SDL_SURFACE.offsetOf("pitch");
    private static final long SURFACE_PIXELS = Layouts.SDL_SURFACE.offsetOf("pixels");
    private static final long DISPLAY_MODE_REFRESH_RATE =
            Layouts.SDL_DISPLAY_MODE.offsetOf("refresh_rate");

    private static final long RECT_SIZE = Layouts.SDL_RECT.byteSize();
    private static final long RECT_X = Layouts.SDL_RECT.offsetOf("x");
    private static final long RECT_Y = Layouts.SDL_RECT.offsetOf("y");
    private static final long RECT_W = Layouts.SDL_RECT.offsetOf("w");
    private static final long RECT_H = Layouts.SDL_RECT.offsetOf("h");

    private static final class Holder {
        private static final SdlVideo INSTANCE = new SdlVideo(NativeLibrary.get().lookup());
    }

    private final MemorySegment createWindow;
    private final MemorySegment createPopupWindow;
    private final MemorySegment setWindowPosition;
    private final MemorySegment setWindowSize;
    private final MemorySegment getWindowPosition;
    private final MemorySegment getDisplayUsableBounds;
    private final MemorySegment destroyWindow;
    private final MemorySegment showWindow;
    private final MemorySegment setWindowTitle;
    private final MemorySegment getWindowSize;
    private final MemorySegment getWindowSizeInPixels;
    private final MemorySegment getWindowDisplayScale;
    private final MemorySegment getDisplayForWindow;
    private final MemorySegment getCurrentDisplayMode;
    private final MemorySegment getWindowId;
    private final MemorySegment getWindowSurface;
    private final MemorySegment updateWindowSurfaceRects;
    private final MemorySegment destroyWindowSurface;
    private final MemorySegment startTextInput;
    private final MemorySegment stopTextInput;
    private final MemorySegment textInputActive;
    private final MemorySegment pollEvent;
    private final MemorySegment waitEventTimeout;
    private final MemorySegment pushEvent;

    private SdlVideo(SymbolLookup lookup) {
        this.createWindow = Downcalls.symbol(lookup, "SDL_CreateWindow");
        this.createPopupWindow = Downcalls.symbol(lookup, "SDL_CreatePopupWindow");
        this.setWindowPosition = Downcalls.symbol(lookup, "SDL_SetWindowPosition");
        this.setWindowSize = Downcalls.symbol(lookup, "SDL_SetWindowSize");
        this.getWindowPosition = Downcalls.symbol(lookup, "SDL_GetWindowPosition");
        this.getDisplayUsableBounds = Downcalls.symbol(lookup, "SDL_GetDisplayUsableBounds");
        this.destroyWindow = Downcalls.symbol(lookup, "SDL_DestroyWindow");
        this.showWindow = Downcalls.symbol(lookup, "SDL_ShowWindow");
        this.setWindowTitle = Downcalls.symbol(lookup, "SDL_SetWindowTitle");
        this.getWindowSize = Downcalls.symbol(lookup, "SDL_GetWindowSize");
        this.getWindowSizeInPixels = Downcalls.symbol(lookup, "SDL_GetWindowSizeInPixels");
        this.getWindowDisplayScale = Downcalls.symbol(lookup, "SDL_GetWindowDisplayScale");
        this.getDisplayForWindow = optionalSymbol(lookup, "SDL_GetDisplayForWindow");
        this.getCurrentDisplayMode = optionalSymbol(lookup, "SDL_GetCurrentDisplayMode");
        this.getWindowId = Downcalls.symbol(lookup, "SDL_GetWindowID");
        this.getWindowSurface = Downcalls.symbol(lookup, "SDL_GetWindowSurface");
        this.updateWindowSurfaceRects = Downcalls.symbol(lookup, "SDL_UpdateWindowSurfaceRects");
        this.destroyWindowSurface = Downcalls.symbol(lookup, "SDL_DestroyWindowSurface");
        this.startTextInput = Downcalls.symbol(lookup, "SDL_StartTextInput");
        this.stopTextInput = Downcalls.symbol(lookup, "SDL_StopTextInput");
        this.textInputActive = Downcalls.symbol(lookup, "SDL_TextInputActive");
        this.pollEvent = Downcalls.symbol(lookup, "SDL_PollEvent");
        this.waitEventTimeout = Downcalls.symbol(lookup, "SDL_WaitEventTimeout");
        this.pushEvent = Downcalls.symbol(lookup, "SDL_PushEvent");
    }

    public static SdlVideo get() {
        return Holder.INSTANCE;
    }

    /// Creates a window.
    ///
    /// `width` and `height` are in SDL's window coordinates, which are logical
    /// pixels on every platform Goldberry targets.
    public SdlWindowHandle createWindow(
            String title, int width, int height, Collection<SdlWindowFlag> flags) {

        MemorySegment pointer;
        try (var arena = Arena.ofConfined()) {
            var titleSegment = arena.allocateFrom(title);
            pointer = callPointer(createWindow, "SDL_CreateWindow",
                    titleSegment, width, height, SdlWindowFlag.mask(flags));
        }
        if (MemorySegment.NULL.equals(pointer)) {
            throw new SdlException("SDL_CreateWindow", Sdl.get().lastError());
        }
        var id = callInt(getWindowId, "SDL_GetWindowID", pointer);
        return new SdlWindowHandle(pointer, id);
    }

    /// Creates a popup window parented to `parent`.
    ///
    /// A popup is a real platform window that is **positioned in its parent's
    /// coordinates** and stays above it — which is what lets a menu or a
    /// dropdown escape the bounds of the window that opened it, the one thing an
    /// in-window overlay cannot do.
    ///
    /// `flags` must contain exactly one of [SdlWindowFlag#POPUP_MENU] and
    /// [SdlWindowFlag#TOOLTIP]; SDL refuses the rest, because every window
    /// manager treats the two differently. Add [SdlWindowFlag#NOT_FOCUSABLE] for
    /// a tooltip: the tooltip flag alone does *not* stop a popup taking focus.
    ///
    /// **Empty when the video driver has no popups.** SDL's `dummy` driver has
    /// none, which is the configuration most headless tests run in; the three
    /// desktop drivers Goldberry ships against — x11, wayland, cocoa and the
    /// Windows one — all do. A caller gets a refusal to handle rather than an
    /// exception, because "this platform has no popup windows" is a fact about
    /// the platform and not a failure ([ADR-0019]).
    ///
    /// @param parent  the window this popup belongs to
    /// @param offsetX x, in the parent's logical coordinates
    /// @param offsetY y, in the parent's logical coordinates
    /// @param width   logical width
    /// @param height  logical height
    /// @param flags   the creation flags, including exactly one popup kind
    /// @return the popup, or empty if the driver does not support popups
    public java.util.Optional<SdlWindowHandle> createPopupWindow(
            SdlWindowHandle parent, int offsetX, int offsetY, int width, int height,
            Collection<SdlWindowFlag> flags) {

        Objects.requireNonNull(parent, "parent");
        var pointer = callPointer(createPopupWindow, "SDL_CreatePopupWindow",
                parent.pointer(), offsetX, offsetY, width, height, SdlWindowFlag.mask(flags));
        if (MemorySegment.NULL.equals(pointer)) {
            var error = Sdl.get().lastError();
            // SDL's own word for "the driver cannot do this", set by
            // SDL_Unsupported(). Anything else — a null parent, conflicting type
            // flags — is a caller's mistake and is thrown.
            if (error != null && error.toLowerCase(java.util.Locale.ROOT).contains("not supported")) {
                LOG.debug("this video driver has no popup windows: {}", error);
                return java.util.Optional.empty();
            }
            throw new SdlException("SDL_CreatePopupWindow", error);
        }
        var id = callInt(getWindowId, "SDL_GetWindowID", pointer);
        return java.util.Optional.of(new SdlWindowHandle(pointer, id));
    }

    /// Where the window's top-left is, in the desktop's coordinates.
    ///
    /// For a popup this is **not** the offset it was created at: SDL reports a
    /// popup's position differently per driver, which is why [SdlWindowHandle]'s
    /// caller remembers what it asked for. For a top-level window it is what
    /// turns a popup's owner-relative placement into the same coordinate space
    /// [#displayUsableBounds] answers in.
    public SdlPoint windowPosition(SdlWindowHandle window) {
        try (var arena = Arena.ofConfined()) {
            var x = arena.allocate(ValueLayout.JAVA_INT);
            var y = arena.allocate(ValueLayout.JAVA_INT);
            if (!callBoolean(getWindowPosition, "SDL_GetWindowPosition",
                    window.pointer(), x, y)) {
                throw new SdlException("SDL_GetWindowPosition", Sdl.get().lastError());
            }
            return new SdlPoint(x.get(ValueLayout.JAVA_INT, 0), y.get(ValueLayout.JAVA_INT, 0));
        }
    }

    /// The part of a display a window may usefully occupy — the full bounds less
    /// whatever the desktop has reserved for a taskbar, a dock or a panel.
    ///
    /// **Not the display's size**, and that is the point: a menu placed against
    /// the screen's bottom edge opens underneath the taskbar. Some drivers cannot
    /// tell the difference and return the full bounds, which is a worse answer
    /// and not a wrong one.
    ///
    /// @param displayId the display the window is on
    private SdlRect displayUsableBounds(int displayId) {
        try (var arena = Arena.ofConfined()) {
            var rect = arena.allocate(RECT_SIZE);
            if (!callBoolean(getDisplayUsableBounds, "SDL_GetDisplayUsableBounds",
                    displayId, rect)) {
                throw new SdlException("SDL_GetDisplayUsableBounds", Sdl.get().lastError());
            }
            return new SdlRect(
                    rect.get(ValueLayout.JAVA_INT, RECT_X),
                    rect.get(ValueLayout.JAVA_INT, RECT_Y),
                    rect.get(ValueLayout.JAVA_INT, RECT_W),
                    rect.get(ValueLayout.JAVA_INT, RECT_H));
        }
    }

    /// [#displayUsableBounds] for the display `window` is currently on.
    ///
    /// Empty when SDL will not say which display that is — the same "no number
    /// to answer with" state [#refreshRate] treats as unknowable rather than as a
    /// failure, and for the same reason: a menu still has to open.
    public java.util.Optional<SdlRect> windowUsableBounds(SdlWindowHandle window) {
        var display = callInt(getDisplayForWindow, "SDL_GetDisplayForWindow",
                window.pointer());
        if (display == 0) {
            return java.util.Optional.empty();
        }
        return java.util.Optional.of(displayUsableBounds(display));
    }

    /// Moves a window. For a popup the coordinates are its parent's; for a
    /// top-level one they are the display's.
    public void setWindowPosition(SdlWindowHandle window, int x, int y) {
        if (!callBoolean(setWindowPosition, "SDL_SetWindowPosition",
                window.pointer(), x, y)) {
            throw new SdlException("SDL_SetWindowPosition", Sdl.get().lastError());
        }
    }

    /// Resizes a window, in logical pixels.
    public void setWindowSize(SdlWindowHandle window, int width, int height) {
        if (!callBoolean(setWindowSize, "SDL_SetWindowSize",
                window.pointer(), width, height)) {
            throw new SdlException("SDL_SetWindowSize", Sdl.get().lastError());
        }
    }

    public void destroyWindow(SdlWindowHandle window) {
        if (window.isDestroyed()) {
            return;
        }
        var pointer = window.pointer();
        window.markDestroyed();
        callVoid(destroyWindow, "SDL_DestroyWindow", pointer);
    }

    public void showWindow(SdlWindowHandle window) {
        if (!callBoolean(showWindow, "SDL_ShowWindow", window.pointer())) {
            throw new SdlException("SDL_ShowWindow", Sdl.get().lastError());
        }
    }

    public void setWindowTitle(SdlWindowHandle window, String title) {
        try (var arena = Arena.ofConfined()) {
            if (!callBoolean(setWindowTitle, "SDL_SetWindowTitle",
                    window.pointer(), arena.allocateFrom(title))) {
                throw new SdlException("SDL_SetWindowTitle", Sdl.get().lastError());
            }
        }
    }

    /// Asks `window` to start delivering `SDL_EVENT_TEXT_INPUT`.
    ///
    /// **SDL3 does not deliver committed text until something asks.** Text input
    /// is off when a window is created, and per window rather than per process,
    /// because turning it on is what raises an on-screen keyboard on a tablet and
    /// what tells an IME where to put its candidate list. Nothing in this toolkit
    /// called it until `text-input` existed to want it, so the `TEXT_INPUT` event
    /// [SdlEventBuffer#committedText()] has always known how to read never arrived
    /// on a real SDL window.
    ///
    /// Idempotent as far as callers are concerned — SDL tracks the state itself
    /// and [#textInputActive] reports it — but not free: on some platforms this
    /// raises a keyboard, so it belongs on focus entering an editable field
    /// rather than on window creation.
    ///
    /// A refusal is **logged rather than thrown**. A platform that will not start
    /// text input is a platform whose keys still arrive; refusing to open the
    /// window over it would be worse than a field that only takes what the key
    /// events carry.
    public void startTextInput(SdlWindowHandle window) {
        if (!callBoolean(startTextInput, "SDL_StartTextInput", window.pointer())) {
            LOG.debug("SDL_StartTextInput() refused: {}", Sdl.get().lastError());
        }
    }

    /// Stops delivering committed text to `window` — what focus leaving the last
    /// editable field does, and what lowers an on-screen keyboard.
    public void stopTextInput(SdlWindowHandle window) {
        if (!callBoolean(stopTextInput, "SDL_StopTextInput", window.pointer())) {
            LOG.debug("SDL_StopTextInput() refused: {}", Sdl.get().lastError());
        }
    }

    /// Whether `window` is currently receiving committed text.
    ///
    /// SDL's own answer rather than a flag kept here, so it stays right across
    /// anything else in the process that touches the same window.
    public boolean textInputActive(SdlWindowHandle window) {
        return callBoolean(textInputActive, "SDL_TextInputActive", window.pointer());
    }

    /// The window's size in logical pixels.
    public SdlSize windowSize(SdlWindowHandle window) {
        return readSize(getWindowSize, "SDL_GetWindowSize", window);
    }

    /// The window's size in physical pixels — the size of its backing store.
    ///
    /// Not the logical size times the scale: SDL knows what the compositor
    /// actually gave it, and on a fractional scale that can differ by a pixel from
    /// anything computed. This is the number the frame must be rasterized at.
    public SdlSize windowSizeInPixels(SdlWindowHandle window) {
        return readSize(getWindowSizeInPixels, "SDL_GetWindowSizeInPixels", window);
    }

    /// The display scale of the monitor this window is on. Fractional in the
    /// ordinary case.
    public float displayScale(SdlWindowHandle window) {
        var scale = callFloat(
                getWindowDisplayScale, "SDL_GetWindowDisplayScale", window.pointer());
        if (scale <= 0f) {
            throw new SdlException("SDL_GetWindowDisplayScale", Sdl.get().lastError());
        }
        return scale;
    }

    /// How many times a second the display this window is on refreshes.
    ///
    /// What the frame loop needs to stop painting frames that are never scanned
    /// out (ADR-0047). Read from the *current* mode rather than the desktop one,
    /// so a window on a second monitor is paced to that monitor.
    ///
    /// Zero is a legitimate answer, not a failure: SDL documents `refresh_rate`
    /// as `0.0f` when unspecified, and some drivers never fill it in. So is a
    /// null mode, which is what SDL returns for a display that has gone away
    /// mid-call. Both mean "no number to pace against", and the caller treats
    /// them the same — throwing here would turn an unknowable refresh rate into
    /// a window that will not open.
    ///
    /// @return the refresh rate in Hz, or 0 if the platform will not say
    public float refreshRate(SdlWindowHandle window) {
        if (getDisplayForWindow == null || getCurrentDisplayMode == null) {
            // A libgoldberry built before these were exported. See
            // optionalSymbol(): an unpaced loop, not a dead window.
            return 0f;
        }
        var displayId = callInt(getDisplayForWindow, "SDL_GetDisplayForWindow", window.pointer());
        if (displayId == 0) {
            return 0f;
        }
        var mode = callPointer(getCurrentDisplayMode, "SDL_GetCurrentDisplayMode", displayId);
        if (MemorySegment.NULL.equals(mode)) {
            return 0f;
        }
        var rate = resizeDisplayMode(mode).get(ValueLayout.JAVA_FLOAT, DISPLAY_MODE_REFRESH_RATE);
        return rate > 0f ? rate : 0f;
    }

    /// Copies a frame into the window's surface and presents the damaged parts.
    ///
    /// `source` is read from its current position; its rows are `sourceStride`
    /// bytes apart and hold `size.width()` 32-bit pixels each. `damage` is a flat
    /// array of `x, y, w, h` quadruples in physical pixels.
    ///
    /// @throws SdlException if the surface is unavailable or in a format that
    ///         cannot be blitted into
    public void present(
            SdlWindowHandle window, ByteBuffer source, int sourceStride, SdlSize size, int[] damage) {

        var traced = LOG.isTraceEnabled();
        var started = traced ? System.nanoTime() : 0L;

        var surface = callPointer(getWindowSurface, "SDL_GetWindowSurface", window.pointer());
        if (MemorySegment.NULL.equals(surface)) {
            throw new SdlException("SDL_GetWindowSurface", Sdl.get().lastError());
        }
        var view = reinterpretSurface(surface);

        var format = view.get(ValueLayout.JAVA_INT, SURFACE_FORMAT);
        if (!SdlPixelFormat.isBlittable(format)) {
            throw new SdlException(
                    "SDL_GetWindowSurface",
                    "the window surface is format 0x" + Integer.toHexString(format)
                            + ", which is not a 32-bit BGRA-order format Goldberry can blit into");
        }

        var surfaceWidth = view.get(ValueLayout.JAVA_INT, SURFACE_WIDTH);
        var surfaceHeight = view.get(ValueLayout.JAVA_INT, SURFACE_HEIGHT);
        if (surfaceWidth != size.width() || surfaceHeight != size.height()) {
            // SDL reallocates the surface on resize; a frame rasterized before
            // that must not be blitted into it.
            throw new SdlException(
                    "SDL_GetWindowSurface",
                    "the surface is " + surfaceWidth + "x" + surfaceHeight
                            + " but the frame is " + size.width() + "x" + size.height());
        }

        var pitch = view.get(ValueLayout.JAVA_INT, SURFACE_PITCH);
        var pixels = view.get(ValueLayout.ADDRESS, SURFACE_PIXELS);
        if (MemorySegment.NULL.equals(pixels)) {
            throw new SdlException("SDL_GetWindowSurface", "the surface has no pixels to write to");
        }

        var gotSurface = traced ? System.nanoTime() : 0L;

        copyRows(source, sourceStride, resizePixels(pixels, (long) pitch * surfaceHeight),
                pitch, size, format);
        var copied = traced ? System.nanoTime() : 0L;

        updateRects(window, damage, size);

        if (traced) {
            var done = System.nanoTime();
            LOG.trace("present {}x{}: getSurface {}us, copy {}us (stride {} -> {}), update {}us",
                    size.width(), size.height(),
                    (gotSurface - started) / 1_000,
                    (copied - gotSurface) / 1_000,
                    sourceStride, pitch,
                    (done - copied) / 1_000);
        }
    }

    /// The window's own drawing surface, as a buffer that can be painted into
    /// directly.
    ///
    /// This is the CPU path without the middle copy: instead of rasterizing into
    /// a buffer of our own and copying it here, the caller paints straight into
    /// the memory SDL is going to upload. The buffer is SDL's, valid until the
    /// window is resized or presented, and must not be kept.
    ///
    /// @throws SdlException if the surface is unavailable or in a format
    ///         Goldberry cannot paint into
    public SurfaceBuffer acquireSurface(SdlWindowHandle window) {
        var surface = callPointer(getWindowSurface, "SDL_GetWindowSurface", window.pointer());
        if (MemorySegment.NULL.equals(surface)) {
            throw new SdlException("SDL_GetWindowSurface", Sdl.get().lastError());
        }
        var view = reinterpretSurface(surface);

        var format = view.get(ValueLayout.JAVA_INT, SURFACE_FORMAT);
        if (!SdlPixelFormat.isBlittable(format)) {
            throw new SdlException(
                    "SDL_GetWindowSurface",
                    "the window surface is format 0x" + Integer.toHexString(format)
                            + ", which is not a 32-bit BGRA-order format Goldberry can paint into");
        }

        var width = view.get(ValueLayout.JAVA_INT, SURFACE_WIDTH);
        var height = view.get(ValueLayout.JAVA_INT, SURFACE_HEIGHT);
        var pitch = view.get(ValueLayout.JAVA_INT, SURFACE_PITCH);
        var pixels = view.get(ValueLayout.ADDRESS, SURFACE_PIXELS);
        if (MemorySegment.NULL.equals(pixels)) {
            throw new SdlException("SDL_GetWindowSurface", "the surface has no pixels to paint into");
        }

        // A ByteBuffer over SDL's memory, not a copy of it -- and a ByteBuffer
        // rather than the segment itself, because a MemorySegment must not leave
        // this module (sec. 3.1).
        return new SurfaceBuffer(
                resizePixels(pixels, (long) pitch * height).asByteBuffer(), width, height, pitch);
    }

    /// Presents a surface that was painted into directly, with no copy.
    public void presentAcquired(SdlWindowHandle window, SdlSize size, int[] damage) {
        updateRects(window, damage, size);
    }

    /// Releases a window's surface, so the next [#present] gets a fresh one.
    ///
    /// Called after a resize: SDL keeps the old surface alive until asked.
    public void invalidateSurface(SdlWindowHandle window) {
        var ignored = callBoolean(
                destroyWindowSurface, "SDL_DestroyWindowSurface", window.pointer());
    }

    /// Takes the next queued event without waiting.
    ///
    /// @return whether an event was written into `buffer`
    public boolean pollEvent(SdlEventBuffer buffer) {
        return callBoolean(pollEvent, "SDL_PollEvent", buffer.segment());
    }

    /// Waits up to `timeoutMillis` for an event.
    ///
    /// This is where the UI thread spends its idle time. It is a block, but not a
    /// stall: it is woken by any event, including the one [#pushWakeup()] posts.
    ///
    /// @return whether an event was written into `buffer`
    public boolean waitEvent(SdlEventBuffer buffer, int timeoutMillis) {
        return callBoolean(waitEventTimeout, "SDL_WaitEventTimeout",
                buffer.segment(), timeoutMillis);
    }

    /// Posts a no-op user event, waking a [#waitEvent] in progress.
    ///
    /// **Safe from any thread.** SDL's event queue takes its own lock, which makes
    /// this the one sanctioned way for background work to reach the UI thread —
    /// and the reason the SPI's `wakeup()` can promise what it promises.
    public void pushWakeup() {
        try (var arena = Arena.ofConfined()) {
            var event = arena.allocate(Layouts.SDL_EVENT.layout());
            event.fill((byte) 0);
            event.set(ValueLayout.JAVA_INT, 0, SdlEventType.USER.value());
            var ignored = callBoolean(pushEvent, "SDL_PushEvent", event);
        }
    }

    /// Pushes a fabricated event onto SDL's queue.
    ///
    /// The event comes back out of [#pollEvent] and [#waitEvent] like any other,
    /// and reaches every event watch on the way in — so what it drives is the
    /// shipping event path rather than a test's imitation of it. Fill the buffer
    /// with [SdlEventBuffer#writeWheel] or
    /// [SdlEventBuffer#writeWindowEvent] first.
    ///
    /// @return whether SDL accepted it; an event watch may refuse one
    public boolean push(SdlEventBuffer buffer) {
        return callBoolean(pushEvent, "SDL_PushEvent",
                Objects.requireNonNull(buffer, "buffer").segment());
    }

    private SdlSize readSize(MemorySegment function, String name, SdlWindowHandle window) {
        try (var arena = Arena.ofConfined()) {
            var width = arena.allocate(ValueLayout.JAVA_INT);
            var height = arena.allocate(ValueLayout.JAVA_INT);
            if (!callBoolean(function, name, window.pointer(), width, height)) {
                throw new SdlException(name, Sdl.get().lastError());
            }
            return new SdlSize(width.get(ValueLayout.JAVA_INT, 0), height.get(ValueLayout.JAVA_INT, 0));
        }
    }

    private void updateRects(SdlWindowHandle window, int[] damage, SdlSize size) {
        if (damage.length % 4 != 0) {
            throw new IllegalArgumentException(
                    "damage must be x,y,w,h quadruples, got " + damage.length + " values");
        }
        var count = damage.length / 4;
        try (var arena = Arena.ofConfined()) {
            MemorySegment rects;
            if (count == 0) {
                // No damage still has to present something the first time, or the
                // window never appears. One whole-window rect is the honest
                // reading of "present this frame".
                rects = arena.allocate(Layouts.SDL_RECT.layout());
                writeRect(rects, 0, 0, 0, size.width(), size.height());
                count = 1;
            } else {
                rects = arena.allocate(RECT_SIZE * count, Layouts.SDL_RECT.byteAlignment());
                for (var i = 0; i < count; i++) {
                    writeRect(rects, i, damage[i * 4], damage[i * 4 + 1],
                            damage[i * 4 + 2], damage[i * 4 + 3]);
                }
            }
            if (!callBoolean(updateWindowSurfaceRects, "SDL_UpdateWindowSurfaceRects",
                    window.pointer(), rects, count)) {
                throw new SdlException("SDL_UpdateWindowSurfaceRects", Sdl.get().lastError());
            }
        }
    }

    private static void writeRect(MemorySegment rects, int index, int x, int y, int w, int h) {
        var base = index * RECT_SIZE;
        rects.set(ValueLayout.JAVA_INT, base, x);
        rects.set(ValueLayout.JAVA_INT, base + 4, y);
        rects.set(ValueLayout.JAVA_INT, base + 8, w);
        rects.set(ValueLayout.JAVA_INT, base + 12, h);
    }

    /// Copies the frame into the surface.
    ///
    /// One copy when the strides agree, row by row when they do not. They usually
    /// do — both sides are `width * 4` for a tightly packed 32-bit image — and the
    /// difference is one memcpy against a thousand of them, which at 1080p is
    /// worth several milliseconds of every frame. SDL is entitled to pad its rows
    /// and Blend2D is entitled to pad its own, so the slow path stays.
    private static void copyRows(
            ByteBuffer source, int sourceStride, MemorySegment target, int targetStride,
            SdlSize size, int format) {

        var rowBytes = Math.multiplyExact(size.width(), 4);
        var sourceSegment = MemorySegment.ofBuffer(source);

        if (sourceStride == targetStride && sourceStride == rowBytes) {
            MemorySegment.copy(
                    sourceSegment, 0, target, 0, (long) rowBytes * size.height());
        } else {
            for (var row = 0; row < size.height(); row++) {
                MemorySegment.copy(
                        sourceSegment, (long) row * sourceStride,
                        target, (long) row * targetStride,
                        rowBytes);
            }
        }
        // XRGB8888 ignores the fourth byte; ARGB8888 reads it as alpha. Blend2D
        // produces premultiplied alpha, which is what a compositor expects, so
        // neither needs a fix-up -- the byte order is identical.
        assert SdlPixelFormat.isBlittable(format);
    }

    // Restricted: the surface pointer arrives zero-length and has to be resized
    // before its fields can be read. Its extent is the struct the layout table
    // verified, so this is a resize to a size the C compiler agreed with.
    @SuppressWarnings("restricted")
    private static MemorySegment reinterpretSurface(MemorySegment surface) {
        return surface.reinterpret(Layouts.SDL_SURFACE.byteSize());
    }

    // Restricted: same obligation as the surface above. SDL owns the mode and
    // keeps it alive for the display's lifetime; the extent is the struct's own
    // size, verified against C by the layout probe.
    @SuppressWarnings("restricted")
    private static MemorySegment resizeDisplayMode(MemorySegment mode) {
        return mode.reinterpret(Layouts.SDL_DISPLAY_MODE.byteSize());
    }

    // Restricted: same, for the pixel store. The extent comes from the surface's
    // own pitch and height, which is exactly the region SDL owns.
    @SuppressWarnings("restricted")
    private static MemorySegment resizePixels(MemorySegment pixels, long bytes) {
        return pixels.reinterpret(bytes);
    }

    // --- invocation helpers -------------------------------------------------
    //
    // One per signature, named for what SDL returns. These used to be a single
    // `invokeWithArguments` taking `Object...`, which boxed every argument on
    // every call and — worse — meant the shape was decided at run time from the
    // arguments rather than at compile time from the constant (ADR-0161).

    private static MemorySegment callPointer(
            MemorySegment function, String name, MemorySegment title, int width, int height,
            long flags) {
        try {
            return (MemorySegment) Downcalls.PTR__PTR_INT_INT_LONG.invokeExact(
                    function, title, width, height, flags);
        } catch (Throwable t) {
            throw failure(name, t);
        }
    }

    private static MemorySegment callPointer(
            MemorySegment function, String name, MemorySegment parent, int offsetX, int offsetY,
            int width, int height, long flags) {
        try {
            return (MemorySegment) Downcalls.PTR__PTR_INT_INT_INT_INT_LONG.invokeExact(
                    function, parent, offsetX, offsetY, width, height, flags);
        } catch (Throwable t) {
            throw failure(name, t);
        }
    }

    private static MemorySegment callPointer(MemorySegment function, String name, int display) {
        try {
            return (MemorySegment) Downcalls.PTR__INT.invokeExact(function, display);
        } catch (Throwable t) {
            throw failure(name, t);
        }
    }

    private static MemorySegment callPointer(
            MemorySegment function, String name, MemorySegment argument) {
        try {
            return (MemorySegment) Downcalls.PTR__PTR.invokeExact(function, argument);
        } catch (Throwable t) {
            throw failure(name, t);
        }
    }

    private static int callInt(MemorySegment function, String name, MemorySegment argument) {
        try {
            return (int) Downcalls.INT__PTR.invokeExact(function, argument);
        } catch (Throwable t) {
            throw failure(name, t);
        }
    }

    private static float callFloat(MemorySegment function, String name, MemorySegment argument) {
        try {
            return (float) Downcalls.FLOAT__PTR.invokeExact(function, argument);
        } catch (Throwable t) {
            throw failure(name, t);
        }
    }

    private static void callVoid(MemorySegment function, String name, MemorySegment argument) {
        try {
            Downcalls.VOID__PTR.invokeExact(function, argument);
        } catch (Throwable t) {
            throw failure(name, t);
        }
    }

    private static boolean callBoolean(
            MemorySegment function, String name, MemorySegment argument) {
        try {
            return (boolean) Downcalls.BOOL__PTR.invokeExact(function, argument);
        } catch (Throwable t) {
            throw failure(name, t);
        }
    }

    private static boolean callBoolean(
            MemorySegment function, String name, int display, MemorySegment out) {
        try {
            return (boolean) Downcalls.BOOL__INT_PTR.invokeExact(function, display, out);
        } catch (Throwable t) {
            throw failure(name, t);
        }
    }

    private static boolean callBoolean(
            MemorySegment function, String name, MemorySegment window, int value) {
        try {
            return (boolean) Downcalls.BOOL__PTR_INT.invokeExact(function, window, value);
        } catch (Throwable t) {
            throw failure(name, t);
        }
    }

    private static boolean callBoolean(
            MemorySegment function, String name, MemorySegment window, MemorySegment argument) {
        try {
            return (boolean) Downcalls.BOOL__PTR_PTR.invokeExact(function, window, argument);
        } catch (Throwable t) {
            throw failure(name, t);
        }
    }

    private static boolean callBoolean(
            MemorySegment function, String name, MemorySegment window, int first, int second) {
        try {
            return (boolean) Downcalls.BOOL__PTR_INT_INT.invokeExact(
                    function, window, first, second);
        } catch (Throwable t) {
            throw failure(name, t);
        }
    }

    private static boolean callBoolean(
            MemorySegment function, String name, MemorySegment window, MemorySegment first,
            MemorySegment second) {
        try {
            return (boolean) Downcalls.BOOL__PTR_PTR_PTR.invokeExact(
                    function, window, first, second);
        } catch (Throwable t) {
            throw failure(name, t);
        }
    }

    private static boolean callBoolean(
            MemorySegment function, String name, MemorySegment window, MemorySegment rects,
            int count) {
        try {
            return (boolean) Downcalls.BOOL__PTR_PTR_INT.invokeExact(
                    function, window, rects, count);
        } catch (Throwable t) {
            throw failure(name, t);
        }
    }

    /// An SdlException raised on the way out of a call is SDL's answer, not a
    /// broken binding, and is rethrown untouched — which is what the old
    /// `invokeWithArguments` helper did and for the same reason.
    private static RuntimeException failure(String name, Throwable cause) {
        if (cause instanceof SdlException sdl) {
            return sdl;
        }
        return new IllegalStateException(name + "() failed", cause);
    }


    /// Binds a symbol the toolkit can do without.
    ///
    /// Everything else here is bound with [Downcalls#symbol], which fails loudly,
    /// because a missing symbol there means a window cannot open and the export
    /// list is simply wrong. The display-mode calls are different: they feed the
    /// frame pacer, which already has a defined answer for "the platform will not
    /// say" — do not pace (ADR-0047). Making them mandatory would mean a
    /// `libgoldberry` built before they were added stops opening windows at all,
    /// to enable an optimization.
    ///
    /// @return the address, or null if this library does not export it
    private static MemorySegment optionalSymbol(SymbolLookup lookup, String symbol) {
        var address = Downcalls.optionalSymbol(lookup, symbol);
        if (address == null) {
            LOG.debug("libgoldberry does not export {}; the frame loop will not be paced"
                    + " to the display", symbol);
        }
        return address;
    }

    /// SDL's own drawing surface, borrowed.
    ///
    /// `pixels` is SDL's memory, not a copy. It stops being valid when the window
    /// is resized or presented.
    public record SurfaceBuffer(ByteBuffer pixels, int width, int height, int stride) {
    }

    /// A size in SDL's terms. Deliberately not `:core`'s `PhysicalSize` — this
    /// module does not depend on that one, and the backend converts.
    /// A point in SDL's window coordinates.
    public record SdlPoint(int x, int y) {
    }

    /// A rectangle in SDL's window coordinates — an `SDL_Rect`, read back.
    public record SdlRect(int x, int y, int width, int height) {
    }

    public record SdlSize(int width, int height) {

        public SdlSize {
            if (width < 0 || height < 0) {
                throw new IllegalArgumentException("negative size " + width + "x" + height);
            }
        }
    }

    /// The event arms Goldberry reads, for callers that want the list.
    public static List<SdlEventType> handledEvents() {
        return List.of(SdlEventType.values());
    }
}
