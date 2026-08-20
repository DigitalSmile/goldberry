package io.github.digitalsmile.goldberry.natives.sdl;

import io.github.digitalsmile.goldberry.natives.Downcalls;
import io.github.digitalsmile.goldberry.natives.NativeLibrary;
import io.github.digitalsmile.goldberry.natives.log.Logs;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.SymbolLookup;
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

    private final MemorySegment createSystemCursor;
    private final MemorySegment setCursor;
    private final MemorySegment destroyCursor;
    private final MemorySegment showCursor;
    private final MemorySegment hideCursor;

    private final Map<SdlSystemCursor, MemorySegment> cursors = new EnumMap<>(SdlSystemCursor.class);
    private SdlSystemCursor current;
    private boolean closed;

    public SdlCursors() {
        this(NativeLibrary.get().lookup());
    }

    SdlCursors(SymbolLookup lookup) {
        this.createSystemCursor = Downcalls.symbol(lookup, "SDL_CreateSystemCursor");
        this.setCursor = Downcalls.symbol(lookup, "SDL_SetCursor");
        this.destroyCursor = Downcalls.symbol(lookup, "SDL_DestroyCursor");
        this.showCursor = Downcalls.symbol(lookup, "SDL_ShowCursor");
        this.hideCursor = Downcalls.symbol(lookup, "SDL_HideCursor");
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
        if (!setCursor(cursor)) {
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
        toggle(showCursor, "SDL_ShowCursor");
    }

    /// Hides the cursor without confining it — what a text editor does while
    /// typing, and what a full-screen player does after a few idle seconds.
    public void hide() {
        toggle(hideCursor, "SDL_HideCursor");
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
            setCursor(fallback);
        }
        for (var entry : cursors.entrySet()) {
            if (!MemorySegment.NULL.equals(entry.getValue())) {
                try {
                    Downcalls.VOID__PTR.invokeExact(destroyCursor, entry.getValue());
                } catch (Throwable t) {
                    throw new IllegalStateException("SDL_DestroyCursor() failed", t);
                }
            }
        }
        cursors.clear();
        current = null;
    }

    private MemorySegment create(SdlSystemCursor shape) {
        MemorySegment cursor;
        try {
            cursor = (MemorySegment) Downcalls.PTR__INT.invokeExact(
                    createSystemCursor, shape.value());
        } catch (Throwable t) {
            throw new IllegalStateException("SDL_CreateSystemCursor() failed", t);
        }
        if (MemorySegment.NULL.equals(cursor)) {
            LOG.debug("SDL has no {} cursor on this platform: {}", shape, Sdl.get().lastError());
        }
        return cursor;
    }

    /// `bool SDL_SetCursor(SDL_Cursor*)` — false when SDL refused it.
    private boolean setCursor(MemorySegment cursor) {
        try {
            return (boolean) Downcalls.BOOL__PTR.invokeExact(setCursor, cursor);
        } catch (Throwable t) {
            throw new IllegalStateException("SDL_SetCursor() failed", t);
        }
    }

    /// `bool SDL_ShowCursor(void)` and its twin. The result is dropped: SDL
    /// returns false only when there is no video subsystem, and there is one by
    /// the time anything here runs.
    private static void toggle(MemorySegment function, String name) {
        try {
            var ignored = (boolean) Downcalls.BOOL__VOID.invokeExact(function);
        } catch (Throwable t) {
            throw new IllegalStateException(name + "() failed", t);
        }
    }
}
