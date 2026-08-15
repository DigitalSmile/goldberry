package io.github.digitalsmile.goldberry.natives.sdl;

import java.lang.foreign.MemorySegment;
import java.util.Objects;

/// An opaque reference to an `SDL_Window`.
///
/// The pointer is package-private on purpose: this class is how a window crosses
/// out of the `natives` module without a `MemorySegment` going with it
/// (`docs/ARCHITECTURE.md` §3.1). Code above the boundary holds one of these and
/// can do nothing with it except hand it back.
///
/// The numeric id is carried alongside because SDL identifies windows by id in
/// events, and looking a pointer up from an id on every event would be a native
/// call per event.
public final class SdlWindowHandle {

    private final MemorySegment pointer;
    private final int id;
    private boolean destroyed;

    SdlWindowHandle(MemorySegment pointer, int id) {
        this.pointer = Objects.requireNonNull(pointer, "pointer");
        this.id = id;
    }

    /// SDL's `SDL_WindowID`, which is what events carry.
    public int id() {
        return id;
    }

    /// Whether this handle still refers to a live SDL window.
    public boolean isDestroyed() {
        return destroyed;
    }

    MemorySegment pointer() {
        if (destroyed) {
            throw new IllegalStateException("SDL window " + id + " has been destroyed");
        }
        return pointer;
    }

    void markDestroyed() {
        destroyed = true;
    }

    @Override
    public String toString() {
        return "SDL_Window#" + id + (destroyed ? " (destroyed)" : "");
    }
}
