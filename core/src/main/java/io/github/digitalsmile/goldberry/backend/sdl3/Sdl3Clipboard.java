package io.github.digitalsmile.goldberry.backend.sdl3;

import io.github.digitalsmile.goldberry.backend.Clipboard;
import io.github.digitalsmile.goldberry.natives.sdl.SdlClipboard;

/// [Clipboard] over SDL3.
///
/// Three forwarding calls and nothing else, which is the whole point of the
/// boundary being here: the native side owns the pointer that
/// `SDL_GetClipboardText` hands back and frees it before returning
/// ([SdlClipboard]), so what crosses into `:core` is a `String` and there is no
/// lifetime for this class to have an opinion about.
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
    public String toString() {
        return "Clipboard[sdl3]";
    }
}
