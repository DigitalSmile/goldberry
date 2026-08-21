package io.github.digitalsmile.goldberry.natives.sdl;

import io.github.digitalsmile.goldberry.natives.Downcalls;
import io.github.digitalsmile.goldberry.natives.NativeLibrary;
import io.github.digitalsmile.goldberry.natives.log.Logs;
import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.SymbolLookup;
import org.slf4j.Logger;

/// SDL3's clipboard calls — the text half.
///
/// The facility `docs/ARCHITECTURE.md` §4 listed and
/// [io.github.digitalsmile.goldberry.backend.Backend] left out, on the rule that
/// an interface with no consumer gets designed twice (ADR-0019). `text-input` is
/// the consumer, and what it needs is the whole of what is here: read the text,
/// write the text, and ask whether there is any.
///
/// **Process-global, like [SdlCursors] and for the same reason** — the clipboard
/// belongs to the session and not to a window. There is no per-window variant to
/// have missed.
///
/// ## Why `SDL_free` is bound
///
/// `SDL_GetClipboardText` returns a string **the caller owns**, allocated by
/// SDL's allocator. Handing that pointer to `free(3)` is undefined whenever SDL
/// was built against a different allocator than the process's — which on Windows
/// is the normal case, not the exotic one — so the matching `SDL_free` is on the
/// export list beside it. It is the only allocator call this toolkit binds, and
/// it exists solely to close this one loop.
///
/// The read is therefore always three steps: call, copy into a Java string, free.
/// Nothing here ever hands a native pointer across the module boundary; what
/// crosses is a `String`.
///
/// Confined to the UI thread, like everything else in this package.
public final class SdlClipboard {

    private static final Logger LOG = Logs.of(SdlClipboard.class);

    private static final class Holder {
        private static final SdlClipboard INSTANCE = new SdlClipboard(NativeLibrary.get().lookup());
    }

    private final MemorySegment getClipboardText;
    private final MemorySegment setClipboardText;
    private final MemorySegment hasClipboardText;
    private final MemorySegment free;

    /// The process's clipboard.
    public static SdlClipboard get() {
        return Holder.INSTANCE;
    }

    SdlClipboard(SymbolLookup lookup) {
        this.getClipboardText = Downcalls.symbol(lookup, "SDL_GetClipboardText");
        this.setClipboardText = Downcalls.symbol(lookup, "SDL_SetClipboardText");
        this.hasClipboardText = Downcalls.symbol(lookup, "SDL_HasClipboardText");
        this.free = Downcalls.symbol(lookup, "SDL_free");
    }

    /// Whether the clipboard holds any non-empty text.
    ///
    /// Worth asking before [#text()], because on X11 and Wayland the read is a
    /// **round trip to the owning client** and this one is answered from what the
    /// compositor already told us.
    public boolean hasText() {
        try {
            return (boolean) Downcalls.BOOL__VOID.invokeExact(hasClipboardText);
        } catch (Throwable t) {
            throw new IllegalStateException("SDL_HasClipboardText() failed", t);
        }
    }

    /// The clipboard's text, or `""` when it holds none.
    ///
    /// Empty rather than null, because "the clipboard is empty" and "the
    /// clipboard holds an empty string" are the same paste — and SDL itself
    /// returns an empty string rather than NULL on failure, so there is no third
    /// state to report.
    public String text() {
        MemorySegment pointer;
        try {
            pointer = (MemorySegment) Downcalls.PTR__VOID.invokeExact(getClipboardText);
        } catch (Throwable t) {
            throw new IllegalStateException("SDL_GetClipboardText() failed", t);
        }
        if (MemorySegment.NULL.equals(pointer)) {
            return "";
        }
        try {
            return readCString(pointer);
        } finally {
            release(pointer);
        }
    }

    /// Puts `text` on the clipboard, replacing whatever was there.
    ///
    /// A refusal is **logged and dropped** rather than thrown. A copy that the
    /// compositor declined is a copy that did not happen, and taking the window
    /// down over it would be worse than the empty paste that follows — the same
    /// argument [SdlCursors] makes for a missing cursor shape.
    ///
    /// @return whether SDL accepted it
    public boolean text(String text) {
        try (var arena = Arena.ofConfined()) {
            var accepted = setText(arena.allocateFrom(text == null ? "" : text));
            if (!accepted) {
                LOG.debug("SDL_SetClipboardText() refused: {}", Sdl.get().lastError());
            }
            return accepted;
        }
    }

    private boolean setText(MemorySegment text) {
        try {
            return (boolean) Downcalls.BOOL__PTR.invokeExact(setClipboardText, text);
        } catch (Throwable t) {
            throw new IllegalStateException("SDL_SetClipboardText() failed", t);
        }
    }

    /// `void SDL_free(void*)` — SDL's allocator, for the string it just handed
    /// over. See the note on this class.
    private void release(MemorySegment pointer) {
        try {
            Downcalls.VOID__PTR.invokeExact(free, pointer);
        } catch (Throwable t) {
            throw new IllegalStateException("SDL_free() failed", t);
        }
    }

    // Restricted: the string's extent is not known until it is walked, which is
    // what reinterpret with an unbounded size is for. SDL guarantees NUL
    // termination for what SDL_GetClipboardText returns.
    @SuppressWarnings("restricted")
    private static String readCString(MemorySegment pointer) {
        return pointer.reinterpret(Long.MAX_VALUE).getString(0);
    }
}
