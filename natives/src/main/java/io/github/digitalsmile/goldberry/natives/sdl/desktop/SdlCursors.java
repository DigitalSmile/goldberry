package io.github.digitalsmile.goldberry.natives.sdl.desktop;

import io.github.digitalsmile.goldberry.natives.sdl.calls.SdlCursorCalls;
import io.github.digitalsmile.goldberry.natives.NativeLibrary;
import io.github.digitalsmile.goldberry.log.Logs;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.SymbolLookup;
import java.util.EnumMap;
import java.util.Map;
import org.slf4j.Logger;
import io.github.digitalsmile.goldberry.natives.sdl.Sdl;
import io.github.digitalsmile.goldberry.natives.sdl.SdlVideo;

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

    private final SdlCursorCalls sdlCursorCalls;

    private final Map<SdlSystemCursor, MemorySegment> cursors = new EnumMap<>(SdlSystemCursor.class);
    private SdlSystemCursor current;
    private boolean closed;

    public SdlCursors() {
        this(NativeLibrary.get().lookup());
    }

    SdlCursors(SymbolLookup lookup) {
        this.sdlCursorCalls = SdlCursorCalls.bind(lookup);
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
        // The result is dropped: SDL returns false only when there is no video
        // subsystem, and there is one by the time anything here runs.
        var ignoredShow = sdlCursorCalls.showCursor().call();
    }

    /// Hides the cursor without confining it — what a text editor does while
    /// typing, and what a full-screen player does after a few idle seconds.
    public void hide() {
        var ignoredHide = sdlCursorCalls.hideCursor().call();
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
                sdlCursorCalls.destroyCursor().call(entry.getValue());
            }
        }
        cursors.clear();
        current = null;
    }

    private MemorySegment create(SdlSystemCursor shape) {
        var cursor = sdlCursorCalls.createSystemCursor().call(shape.value());
        if (MemorySegment.NULL.equals(cursor)) {
            LOG.debug("SDL has no {} cursor on this platform: {}", shape, Sdl.get().lastError());
        }
        return cursor;
    }

    /// `bool SDL_SetCursor(SDL_Cursor*)` — false when SDL refused it.
    private boolean setCursor(MemorySegment cursor) {
        return sdlCursorCalls.setCursor().call(cursor);
    }

}
