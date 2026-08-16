package io.github.digitalsmile.goldberry.natives.sdl;

import io.github.digitalsmile.goldberry.natives.NativeLibrary;
import io.github.digitalsmile.goldberry.natives.log.Logs;
import java.lang.foreign.FunctionDescriptor;
import java.lang.foreign.Linker;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.SymbolLookup;
import java.lang.foreign.ValueLayout;
import java.lang.invoke.MethodHandle;
import java.util.EnumMap;
import java.util.Map;
import org.slf4j.Logger;

/// SDL3's mouse cursor calls.
///
/// The shapes themselves never leave this class. `SDL_CreateSystemCursor` hands
/// back an `SDL_Cursor *` that has to be destroyed exactly once, and a pointer
/// that crossed into `:core` would be a lifetime that two modules share — so the
/// cursors are created here on first use, cached by shape, and destroyed
/// together. What crosses the boundary is an [SdlSystemCursor], which is an enum.
///
/// Cursors are **process-global in SDL**, not per window: `SDL_SetCursor` sets
/// what the mouse looks like everywhere. That is not a limitation to work around
/// but the platform's actual model — X11, Wayland and Win32 all set the cursor
/// for whichever surface the pointer is over, and the pointer is over one window
/// at a time. The backend calls this for the window the pointer is in.
///
/// Confined to the UI thread, like everything else in [SdlVideo].
public final class SdlCursors implements AutoCloseable {

    private static final Logger LOG = Logs.of(SdlCursors.class);

    private static final Linker LINKER = Linker.nativeLinker();

    private final MethodHandle createSystemCursor;
    private final MethodHandle setCursor;
    private final MethodHandle destroyCursor;
    private final MethodHandle showCursor;
    private final MethodHandle hideCursor;

    private final Map<SdlSystemCursor, MemorySegment> cursors = new EnumMap<>(SdlSystemCursor.class);
    private SdlSystemCursor current;
    private boolean closed;

    public SdlCursors() {
        this(NativeLibrary.get().lookup());
    }

    SdlCursors(SymbolLookup lookup) {
        this.createSystemCursor = downcall(lookup, "SDL_CreateSystemCursor",
                FunctionDescriptor.of(ValueLayout.ADDRESS, ValueLayout.JAVA_INT));
        this.setCursor = downcall(lookup, "SDL_SetCursor",
                FunctionDescriptor.of(ValueLayout.JAVA_BOOLEAN, ValueLayout.ADDRESS));
        this.destroyCursor = downcall(lookup, "SDL_DestroyCursor",
                FunctionDescriptor.ofVoid(ValueLayout.ADDRESS));
        this.showCursor = downcall(lookup, "SDL_ShowCursor",
                FunctionDescriptor.of(ValueLayout.JAVA_BOOLEAN));
        this.hideCursor = downcall(lookup, "SDL_HideCursor",
                FunctionDescriptor.of(ValueLayout.JAVA_BOOLEAN));
    }

    /// Shows `shape`, creating it the first time it is asked for.
    ///
    /// Repeating a shape is free: SDL is only told when the shape actually
    /// changes, which matters because this is called from pointer motion and the
    /// answer is the same for every pixel of a drag.
    ///
    /// A shape the platform declines to provide is **logged and skipped**, not
    /// thrown: a missing cursor theme leaves the arrow where it was, and that is
    /// a far better outcome than a window that will not track the pointer.
    public void set(SdlSystemCursor shape) {
        if (closed || shape == current) {
            return;
        }
        var cursor = cursors.computeIfAbsent(shape, this::create);
        if (MemorySegment.NULL.equals(cursor)) {
            return;
        }
        if (!(boolean) invoke(setCursor, "SDL_SetCursor", cursor)) {
            LOG.debug("SDL_SetCursor({}) refused: {}", shape, Sdl.get().lastError());
            return;
        }
        current = shape;
    }

    /// The shape currently shown, or null before anything has been set.
    public SdlSystemCursor current() {
        return current;
    }

    /// Makes the cursor visible. It is by default.
    public void show() {
        invoke(showCursor, "SDL_ShowCursor");
    }

    /// Hides the cursor without confining it — what a text editor does while
    /// typing, and what a full-screen player does after a few idle seconds.
    public void hide() {
        invoke(hideCursor, "SDL_HideCursor");
    }

    /// Destroys every cursor created here.
    ///
    /// Idempotent, and resets to SDL's default first: destroying the cursor that
    /// is currently set leaves SDL pointing at freed memory, which it documents
    /// and which nothing else here would notice.
    @Override
    public void close() {
        if (closed) {
            return;
        }
        closed = true;
        var fallback = cursors.get(SdlSystemCursor.DEFAULT);
        if (fallback != null && current != null && current != SdlSystemCursor.DEFAULT) {
            invoke(setCursor, "SDL_SetCursor", fallback);
        }
        for (var entry : cursors.entrySet()) {
            if (!MemorySegment.NULL.equals(entry.getValue())) {
                invoke(destroyCursor, "SDL_DestroyCursor", entry.getValue());
            }
        }
        cursors.clear();
        current = null;
    }

    private MemorySegment create(SdlSystemCursor shape) {
        var cursor = (MemorySegment) invoke(createSystemCursor, "SDL_CreateSystemCursor", shape.value());
        if (MemorySegment.NULL.equals(cursor)) {
            LOG.debug("SDL has no {} cursor on this platform: {}", shape, Sdl.get().lastError());
        }
        return cursor;
    }

    private static Object invoke(MethodHandle handle, String name, Object... args) {
        try {
            return handle.invokeWithArguments(args);
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
}
