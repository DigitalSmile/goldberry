package io.github.digitalsmile.goldberry.natives.sdl;

import io.github.digitalsmile.goldberry.natives.NativeLibrary;
import io.github.digitalsmile.goldberry.natives.layout.Layouts;
import java.lang.foreign.Arena;
import java.lang.foreign.FunctionDescriptor;
import java.lang.foreign.Linker;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.SymbolLookup;
import java.lang.foreign.ValueLayout;
import java.lang.invoke.MethodHandle;
import java.nio.ByteBuffer;
import java.util.Collection;
import java.util.List;

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

    private static final Linker LINKER = Linker.nativeLinker();

    private static final long SURFACE_FORMAT = Layouts.SDL_SURFACE.offsetOf("format");
    private static final long SURFACE_WIDTH = Layouts.SDL_SURFACE.offsetOf("w");
    private static final long SURFACE_HEIGHT = Layouts.SDL_SURFACE.offsetOf("h");
    private static final long SURFACE_PITCH = Layouts.SDL_SURFACE.offsetOf("pitch");
    private static final long SURFACE_PIXELS = Layouts.SDL_SURFACE.offsetOf("pixels");

    private static final long RECT_SIZE = Layouts.SDL_RECT.byteSize();

    private static final class Holder {
        private static final SdlVideo INSTANCE = new SdlVideo(NativeLibrary.get().lookup());
    }

    private final MethodHandle createWindow;
    private final MethodHandle destroyWindow;
    private final MethodHandle showWindow;
    private final MethodHandle setWindowTitle;
    private final MethodHandle getWindowSize;
    private final MethodHandle getWindowSizeInPixels;
    private final MethodHandle getWindowDisplayScale;
    private final MethodHandle getWindowId;
    private final MethodHandle getWindowSurface;
    private final MethodHandle updateWindowSurfaceRects;
    private final MethodHandle destroyWindowSurface;
    private final MethodHandle pollEvent;
    private final MethodHandle waitEventTimeout;
    private final MethodHandle pushEvent;

    private SdlVideo(SymbolLookup lookup) {
        this.createWindow = downcall(lookup, "SDL_CreateWindow", FunctionDescriptor.of(
                ValueLayout.ADDRESS, ValueLayout.ADDRESS, ValueLayout.JAVA_INT,
                ValueLayout.JAVA_INT, ValueLayout.JAVA_LONG));
        this.destroyWindow = downcall(lookup, "SDL_DestroyWindow",
                FunctionDescriptor.ofVoid(ValueLayout.ADDRESS));
        this.showWindow = downcall(lookup, "SDL_ShowWindow",
                FunctionDescriptor.of(ValueLayout.JAVA_BOOLEAN, ValueLayout.ADDRESS));
        this.setWindowTitle = downcall(lookup, "SDL_SetWindowTitle",
                FunctionDescriptor.of(ValueLayout.JAVA_BOOLEAN, ValueLayout.ADDRESS, ValueLayout.ADDRESS));
        this.getWindowSize = downcall(lookup, "SDL_GetWindowSize", FunctionDescriptor.of(
                ValueLayout.JAVA_BOOLEAN, ValueLayout.ADDRESS, ValueLayout.ADDRESS, ValueLayout.ADDRESS));
        this.getWindowSizeInPixels = downcall(lookup, "SDL_GetWindowSizeInPixels", FunctionDescriptor.of(
                ValueLayout.JAVA_BOOLEAN, ValueLayout.ADDRESS, ValueLayout.ADDRESS, ValueLayout.ADDRESS));
        this.getWindowDisplayScale = downcall(lookup, "SDL_GetWindowDisplayScale",
                FunctionDescriptor.of(ValueLayout.JAVA_FLOAT, ValueLayout.ADDRESS));
        this.getWindowId = downcall(lookup, "SDL_GetWindowID",
                FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.ADDRESS));
        this.getWindowSurface = downcall(lookup, "SDL_GetWindowSurface",
                FunctionDescriptor.of(ValueLayout.ADDRESS, ValueLayout.ADDRESS));
        this.updateWindowSurfaceRects = downcall(lookup, "SDL_UpdateWindowSurfaceRects",
                FunctionDescriptor.of(ValueLayout.JAVA_BOOLEAN, ValueLayout.ADDRESS,
                        ValueLayout.ADDRESS, ValueLayout.JAVA_INT));
        this.destroyWindowSurface = downcall(lookup, "SDL_DestroyWindowSurface",
                FunctionDescriptor.of(ValueLayout.JAVA_BOOLEAN, ValueLayout.ADDRESS));
        this.pollEvent = downcall(lookup, "SDL_PollEvent",
                FunctionDescriptor.of(ValueLayout.JAVA_BOOLEAN, ValueLayout.ADDRESS));
        this.waitEventTimeout = downcall(lookup, "SDL_WaitEventTimeout",
                FunctionDescriptor.of(ValueLayout.JAVA_BOOLEAN, ValueLayout.ADDRESS, ValueLayout.JAVA_INT));
        this.pushEvent = downcall(lookup, "SDL_PushEvent",
                FunctionDescriptor.of(ValueLayout.JAVA_BOOLEAN, ValueLayout.ADDRESS));
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
            pointer = (MemorySegment) invoke(
                    createWindow, "SDL_CreateWindow",
                    titleSegment, width, height, SdlWindowFlag.mask(flags));
        }
        if (MemorySegment.NULL.equals(pointer)) {
            throw new SdlException("SDL_CreateWindow", Sdl.get().lastError());
        }
        var id = (int) invoke(getWindowId, "SDL_GetWindowID", pointer);
        return new SdlWindowHandle(pointer, id);
    }

    public void destroyWindow(SdlWindowHandle window) {
        if (window.isDestroyed()) {
            return;
        }
        var pointer = window.pointer();
        window.markDestroyed();
        invoke(destroyWindow, "SDL_DestroyWindow", pointer);
    }

    public void showWindow(SdlWindowHandle window) {
        if (!(boolean) invoke(showWindow, "SDL_ShowWindow", window.pointer())) {
            throw new SdlException("SDL_ShowWindow", Sdl.get().lastError());
        }
    }

    public void setWindowTitle(SdlWindowHandle window, String title) {
        try (var arena = Arena.ofConfined()) {
            if (!(boolean) invoke(setWindowTitle, "SDL_SetWindowTitle",
                    window.pointer(), arena.allocateFrom(title))) {
                throw new SdlException("SDL_SetWindowTitle", Sdl.get().lastError());
            }
        }
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
        var scale = (float) invoke(getWindowDisplayScale, "SDL_GetWindowDisplayScale", window.pointer());
        if (scale <= 0f) {
            throw new SdlException("SDL_GetWindowDisplayScale", Sdl.get().lastError());
        }
        return scale;
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

        var surface = (MemorySegment) invoke(getWindowSurface, "SDL_GetWindowSurface", window.pointer());
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

        copyRows(source, sourceStride, resizePixels(pixels, (long) pitch * surfaceHeight),
                pitch, size, format);

        updateRects(window, damage, size);
    }

    /// Releases a window's surface, so the next [#present] gets a fresh one.
    ///
    /// Called after a resize: SDL keeps the old surface alive until asked.
    public void invalidateSurface(SdlWindowHandle window) {
        invoke(destroyWindowSurface, "SDL_DestroyWindowSurface", window.pointer());
    }

    /// Takes the next queued event without waiting.
    ///
    /// @return whether an event was written into `buffer`
    public boolean pollEvent(SdlEventBuffer buffer) {
        return (boolean) invoke(pollEvent, "SDL_PollEvent", buffer.segment());
    }

    /// Waits up to `timeoutMillis` for an event.
    ///
    /// This is where the UI thread spends its idle time. It is a block, but not a
    /// stall: it is woken by any event, including the one [#pushWakeup()] posts.
    ///
    /// @return whether an event was written into `buffer`
    public boolean waitEvent(SdlEventBuffer buffer, int timeoutMillis) {
        return (boolean) invoke(waitEventTimeout, "SDL_WaitEventTimeout",
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
            invoke(pushEvent, "SDL_PushEvent", event);
        }
    }

    private SdlSize readSize(MethodHandle handle, String name, SdlWindowHandle window) {
        try (var arena = Arena.ofConfined()) {
            var width = arena.allocate(ValueLayout.JAVA_INT);
            var height = arena.allocate(ValueLayout.JAVA_INT);
            if (!(boolean) invoke(handle, name, window.pointer(), width, height)) {
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
            if (!(boolean) invoke(updateWindowSurfaceRects, "SDL_UpdateWindowSurfaceRects",
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

    /// Copies the frame row by row.
    ///
    /// Row-wise rather than one bulk copy because the two strides rarely match:
    /// SDL pads surface rows to its own alignment, and Blend2D pads to its own.
    private static void copyRows(
            ByteBuffer source, int sourceStride, MemorySegment target, int targetStride,
            SdlSize size, int format) {

        var rowBytes = Math.multiplyExact(size.width(), 4);
        var sourceSegment = MemorySegment.ofBuffer(source);
        for (var row = 0; row < size.height(); row++) {
            MemorySegment.copy(
                    sourceSegment, (long) row * sourceStride,
                    target, (long) row * targetStride,
                    rowBytes);
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

    // Restricted: same, for the pixel store. The extent comes from the surface's
    // own pitch and height, which is exactly the region SDL owns.
    @SuppressWarnings("restricted")
    private static MemorySegment resizePixels(MemorySegment pixels, long bytes) {
        return pixels.reinterpret(bytes);
    }

    private static Object invoke(MethodHandle handle, String name, Object... args) {
        try {
            return handle.invokeWithArguments(args);
        } catch (SdlException e) {
            throw e;
        } catch (Throwable t) {
            throw new IllegalStateException(name + "() failed", t);
        }
    }

    // Restricted: see GoldberryShim.downcall -- same obligation, same reason.
    @SuppressWarnings("restricted")
    private static MethodHandle downcall(SymbolLookup lookup, String symbol, FunctionDescriptor descriptor) {
        var address = lookup.find(symbol).orElseThrow(() -> new UnsatisfiedLinkError(
                "libgoldberry does not export " + symbol
                        + " — is it listed in natives/src/main/cmake/exports/goldberry.symbols?"));
        return LINKER.downcallHandle(address, descriptor);
    }

    /// A size in SDL's terms. Deliberately not `:core`'s `PhysicalSize` — this
    /// module does not depend on that one, and the backend converts.
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
