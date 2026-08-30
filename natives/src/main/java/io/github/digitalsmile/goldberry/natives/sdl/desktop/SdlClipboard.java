package io.github.digitalsmile.goldberry.natives.sdl.desktop;

import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.SymbolLookup;

import org.slf4j.Logger;

import io.github.digitalsmile.goldberry.log.Logs;
import io.github.digitalsmile.goldberry.natives.NativeLibrary;
import io.github.digitalsmile.goldberry.natives.sdl.Sdl;
import io.github.digitalsmile.goldberry.natives.sdl.calls.SdlClipboardCalls;

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
        private static final SdlClipboard INSTANCE =
                new SdlClipboard(NativeLibrary.get().lookup());
    }

    private final SdlClipboardCalls sdlClipboardCalls;

    /// The process's clipboard.
    public static SdlClipboard get() {
        return Holder.INSTANCE;
    }

    SdlClipboard(SymbolLookup lookup) {
        this.sdlClipboardCalls = SdlClipboardCalls.bind(lookup);
    }

    /// Whether the clipboard holds any non-empty text.
    ///
    /// Worth asking before [#text()], because on X11 and Wayland the read is a
    /// **round trip to the owning client** and this one is answered from what the
    /// compositor already told us.
    public boolean hasText() {
        return sdlClipboardCalls.hasClipboardText().call();
    }

    /// The clipboard's text, or `""` when it holds none.
    ///
    /// Empty rather than null, because "the clipboard is empty" and "the
    /// clipboard holds an empty string" are the same paste — and SDL itself
    /// returns an empty string rather than NULL on failure, so there is no third
    /// state to report.
    public String text() {
        var pointer = sdlClipboardCalls.getClipboardText().call();
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
            var accepted = sdlClipboardCalls.setClipboardText().call(arena.allocateFrom(text == null ? "" : text));
            if (!accepted) {
                LOG.debug("SDL_SetClipboardText() refused: {}", Sdl.get().lastError());
            }
            return accepted;
        }
    }

    /// `void SDL_free(void*)` — SDL's allocator, for the string it just handed
    /// over. See the note on this class.
    private void release(MemorySegment pointer) {
        sdlClipboardCalls.free().call(pointer);
    }

    // Restricted: the string's extent is not known until it is walked, which is
    // what reinterpret with an unbounded size is for. SDL guarantees NUL
    // termination for what SDL_GetClipboardText returns.
    @SuppressWarnings("restricted")
    private static String readCString(MemorySegment pointer) {
        return pointer.reinterpret(Long.MAX_VALUE).getString(0);
    }
}
