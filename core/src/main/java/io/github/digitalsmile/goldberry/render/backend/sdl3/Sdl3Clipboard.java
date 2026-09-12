package io.github.digitalsmile.goldberry.render.backend.sdl3;

import io.github.digitalsmile.goldberry.natives.sdl.desktop.SdlClipboard;
import io.github.digitalsmile.goldberry.render.Clipboard;

/// [Clipboard] over SDL3.
///
/// Forwarding calls and nothing else, which is the whole point of the boundary
/// being here: the native side owns the pointer that `SDL_GetClipboardText` and
/// `SDL_GetClipboardData` hand back and frees it before returning
/// ([SdlClipboard]), and it owns the arena a lazy offer is served from — so what
/// crosses into `:core` is a `String` or a `byte[]` and there is no lifetime for
/// this class to have an opinion about (ADR-0286).
///
/// Confined to the UI thread, like the rest of this backend.
final class Sdl3Clipboard implements Clipboard {

    private final SdlClipboard clipboard = SdlClipboard.get();

    @Override
    public boolean hasText() {
        return clipboard.hasText();
    }

    @Override
    public String text() {
        return clipboard.text();
    }

    @Override
    public boolean text(String text) {
        return clipboard.text(text);
    }

    @Override
    public boolean has(String mime) {
        return clipboard.has(mime);
    }

    @Override
    public byte[] read(String mime) {
        return clipboard.read(mime);
    }

    @Override
    public java.util.List<String> types() {
        return clipboard.types();
    }

    @Override
    public boolean write(java.util.Map<String, byte[]> byMime) {
        return clipboard.write(byMime);
    }

    @Override
    public boolean clear() {
        return clipboard.clear();
    }

    @Override
    public String toString() {
        return "Clipboard[sdl3]";
    }
}
