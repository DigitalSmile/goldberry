package dev.goldberry.natives.sdl.desktop;

import java.lang.foreign.MemorySegment;
import java.lang.foreign.SymbolLookup;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.function.Supplier;

import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;

import dev.goldberry.log.Logs;
import dev.goldberry.natives.NativeLibrary;
import dev.goldberry.natives.sdl.Sdl;
import dev.goldberry.natives.sdl.SdlVideo;
import dev.goldberry.natives.sdl.calls.SdlCursorCalls;
import dev.goldberry.natives.sdl.calls.SdlSurfaceCalls;
import dev.goldberry.natives.sdl.window.SdlIconImage;
import dev.goldberry.natives.sdl.window.SdlPixelFormat;

/// SDL3's mouse cursor calls.
///
/// The shapes themselves never leave this class. `SDL_CreateSystemCursor` hands
/// back an `SDL_Cursor *` that has to be destroyed exactly once, and a pointer
/// that crossed into `:core` would be a lifetime that two modules share — so the
/// cursors are created here on first use, cached by shape, and destroyed
/// together. What crosses the boundary is an [SdlSystemCursor], which is an enum,
/// or a key the caller chose for a cursor drawn from its own picture.
///
/// Cursors are **process-global in SDL**, not per window: `SDL_SetCursor` sets
/// what the mouse looks like everywhere. That is not a limitation to work around
/// but the platform's actual model — X11, Wayland and Win32 all set the cursor
/// for whichever surface the pointer is over, and the pointer is over one window
/// at a time. The backend calls this for the window the pointer is in.
///
/// Confined to the UI thread, like everything else in [SdlVideo].
///
/// Read more: [The native boundary](https://goldberry.dev/docs/overview/architecture.html#the-native-boundary).
public final class SdlCursors implements AutoCloseable {

    private static final Logger LOG = Logs.of(SdlCursors.class);

    /// What makes SDL pick a picture cursor's alternate by the display's
    /// scale on Windows, where it is off unless asked for. Wayland and macOS
    /// pick by scale without it.
    static final String DPI_SCALE_CURSORS_HINT = "SDL_MOUSE_DPI_SCALE_CURSORS";

    private final SdlCursorCalls sdlCursorCalls;
    private final SdlSurfaceCalls sdlSurfaceCalls;

    private final Map<SdlSystemCursor, MemorySegment> cursors = new EnumMap<>(SdlSystemCursor.class);
    private final Map<Object, MemorySegment> pictures = new HashMap<>();

    /// What is shown: an [SdlSystemCursor], a picture's key, or null before
    /// anything has been set.
    private @Nullable Object current;

    private boolean hinted;
    private boolean closed;

    public SdlCursors() {
        this(NativeLibrary.get().lookup());
    }

    SdlCursors(SymbolLookup lookup) {
        this.sdlCursorCalls = SdlCursorCalls.bind(lookup);
        this.sdlSurfaceCalls = SdlSurfaceCalls.bind(lookup);
    }

    /// Shows `shape`, creating it the first time it is asked for.
    ///
    /// Repeating a shape is free: SDL is only told when the shape actually
    /// changes, which matters because this is called from pointer motion and the
    /// answer is the same for every pixel of a drag.
    ///
    /// A shape the platform declines to provide is **logged and skipped**, not
    /// thrown: a missing cursor theme leaves the arrow where it was, and that is
    /// a far better outcome than a window that will not track the pointer. When
    /// a picture is showing, the default arrow replaces it instead.
    public void set(SdlSystemCursor shape) {
        if (closed || shape == current) {
            return;
        }
        var cursor = cursors.computeIfAbsent(shape, this::create);
        if (MemorySegment.NULL.equals(cursor)) {
            // A picture left up would be a shape nobody asked for, where the
            // platform's arrow is only a less precise one.
            if (shape != SdlSystemCursor.DEFAULT && current != null && !(current instanceof SdlSystemCursor)) {
                set(SdlSystemCursor.DEFAULT);
            }
            return;
        }
        if (!setCursor(cursor)) {
            LOG.debug("SDL_SetCursor({}) refused: {}", shape, Sdl.get().lastError());
            return;
        }
        current = shape;
    }

    /// Shows the cursor drawn from a picture that `key` names, making it from
    /// `picture` the first time the key is asked for.
    ///
    /// The key is the caller's: equal keys are one cursor, so a caller that
    /// shows a different size of a shape at another display scale gives it a
    /// different key. Repeating the key shown is free, as repeating a shape is.
    ///
    /// @return false when SDL could not make or show the cursor, and the caller
    ///         should show a system shape instead
    public boolean set(Object key, Supplier<SdlColorCursor> picture) {
        Objects.requireNonNull(key, "key");
        Objects.requireNonNull(picture, "picture");
        if (closed) {
            return false;
        }
        if (key.equals(current)) {
            return true;
        }
        var cursor = pictures.computeIfAbsent(key, _ -> createColor(picture.get()));
        if (MemorySegment.NULL.equals(cursor)) {
            return false;
        }
        if (!setCursor(cursor)) {
            LOG.debug("SDL_SetCursor({}) refused: {}", key, Sdl.get().lastError());
            return false;
        }
        current = key;
        return true;
    }

    /// Destroys every cursor made from a picture, which is what replacing the
    /// pictures means. The one shown, if it was one of them, goes back to SDL's
    /// default until the next [#set].
    public void forgetPictures() {
        for (var cursor : pictures.values()) {
            if (!MemorySegment.NULL.equals(cursor)) {
                sdlCursorCalls.destroyCursor().call(cursor);
            }
        }
        pictures.clear();
        if (current != null && !(current instanceof SdlSystemCursor)) {
            current = null;
        }
    }

    /// The system shape currently shown, or null when it is a picture or
    /// nothing has been set.
    public @Nullable SdlSystemCursor current() {
        return current instanceof SdlSystemCursor shape ? shape : null;
    }

    /// The key of the picture currently shown, or null when it is a system
    /// shape or nothing has been set.
    public @Nullable Object currentPicture() {
        return current instanceof SdlSystemCursor ? null : current;
    }

    /// Makes the cursor visible. It is by default.
    public void show() {
        // The result is dropped: SDL returns false only when there is no video
        // subsystem, and there is one by the time anything here runs.
        var _ = sdlCursorCalls.showCursor().call();
    }

    /// Hides the cursor without confining it — what a text editor does while
    /// typing, and what a full-screen player does after a few idle seconds.
    public void hide() {
        var _ = sdlCursorCalls.hideCursor().call();
    }

    /// Destroys every cursor created here.
    ///
    /// Idempotent. It used to set the default cursor first, on the grounds that
    /// destroying the one currently shown would leave SDL pointing at freed
    /// memory — which was true of SDL 2 and is not true of SDL 3:
    /// `SDL_DestroyCursor` says in as many words that it reverts to the default
    /// cursor if the one being destroyed is active. So the reset was a call into
    /// SDL to prevent something SDL already prevents, made at shutdown, in an
    /// order that mattered.
    @Override
    public void close() {
        if (closed) {
            return;
        }
        closed = true;
        for (var entry : cursors.entrySet()) {
            if (!MemorySegment.NULL.equals(entry.getValue())) {
                sdlCursorCalls.destroyCursor().call(entry.getValue());
            }
        }
        cursors.clear();
        forgetPictures();
        current = null;
    }

    private MemorySegment create(SdlSystemCursor shape) {
        var cursor = sdlCursorCalls.createSystemCursor().call(shape.value());
        if (MemorySegment.NULL.equals(cursor)) {
            LOG.debug(
                    "SDL has no {} cursor on this platform: {}",
                    shape,
                    Sdl.get().lastError());
        }
        return cursor;
    }

    /// A cursor from `picture`, its larger sizes hung off the first as
    /// alternates, or NULL when SDL could not make one.
    ///
    /// The surfaces are views over the caller's buffers and are destroyed
    /// before this returns: SDL copies what it shows.
    private MemorySegment createColor(SdlColorCursor picture) {
        if (!hinted) {
            hinted = true;
            if (!Sdl.get().setHint(DPI_SCALE_CURSORS_HINT, "1")) {
                LOG.debug("SDL kept {} as it was", DPI_SCALE_CURSORS_HINT);
            }
        }
        var images = picture.images();
        var base = surfaceOf(images.getFirst());
        if (MemorySegment.NULL.equals(base)) {
            return MemorySegment.NULL;
        }
        try {
            for (var alternate : images.subList(1, images.size())) {
                var surface = surfaceOf(alternate);
                if (MemorySegment.NULL.equals(surface)) {
                    continue;
                }
                try {
                    if (!sdlSurfaceCalls.addSurfaceAlternateImage().call(base, surface)) {
                        LOG.debug(
                                "SDL kept no {}x{} size of a cursor: {}",
                                alternate.width(),
                                alternate.height(),
                                Sdl.get().lastError());
                    }
                } finally {
                    sdlSurfaceCalls.destroySurface().call(surface);
                }
            }
            var cursor = sdlCursorCalls.createColorCursor().call(base, picture.hotX(), picture.hotY());
            if (MemorySegment.NULL.equals(cursor)) {
                LOG.debug("SDL made no cursor from a picture: {}", Sdl.get().lastError());
            }
            return cursor;
        } finally {
            sdlSurfaceCalls.destroySurface().call(base);
        }
    }

    private MemorySegment surfaceOf(SdlIconImage image) {
        var surface = sdlSurfaceCalls
                .createSurfaceFrom()
                .call(
                        image.width(),
                        image.height(),
                        SdlPixelFormat.ARGB8888.value(),
                        MemorySegment.ofBuffer(image.pixels()),
                        image.stride());
        if (MemorySegment.NULL.equals(surface)) {
            LOG.debug(
                    "SDL_CreateSurfaceFrom refused a cursor's picture: {}",
                    Sdl.get().lastError());
        }
        return surface;
    }

    /// `bool SDL_SetCursor(SDL_Cursor*)` — false when SDL refused it.
    private boolean setCursor(MemorySegment cursor) {
        return sdlCursorCalls.setCursor().call(cursor);
    }
}
