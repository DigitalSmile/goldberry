package dev.goldberry.render.backend.sdl3;

import java.util.List;
import java.util.Map;

import dev.goldberry.bind.Subscription;
import dev.goldberry.natives.sdl.desktop.SdlClipboard;
import dev.goldberry.render.clipboard.Clipboard;
import dev.goldberry.render.clipboard.ClipboardListeners;

/// [Clipboard] over SDL3.
///
/// Forwarding calls and nothing else, which is the whole point of the boundary
/// being here: the native side owns the pointer that `SDL_GetClipboardText` and
/// `SDL_GetClipboardData` hand back and frees it before returning
/// ([SdlClipboard]), and it owns the arena a lazy offer is served from — so what
/// crosses into `:core` is a `String` or a `byte[]` and there is no lifetime for
/// this class to have an opinion about.
///
/// Confined to the UI thread, like the rest of this backend.
final class Sdl3Clipboard implements Clipboard {

    private final SdlClipboard clipboard = SdlClipboard.get();

    private final ClipboardListeners listeners = new ClipboardListeners();

    /// `SDL_EVENT_CLIPBOARD_UPDATE` arrived: somebody, this process included,
    /// changed what the clipboard holds.
    void changed() {
        listeners.changed();
    }

    @Override
    public Subscription onChange(Runnable listener) {
        return listeners.add(listener);
    }

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
    public List<String> types() {
        return clipboard.types();
    }

    @Override
    public boolean write(Map<String, byte[]> byMime) {
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
