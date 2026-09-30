package io.github.digitalsmile.goldberry.render.backend.sdl3;

import io.github.digitalsmile.goldberry.natives.sdl.desktop.SdlClipboard;
import io.github.digitalsmile.goldberry.render.clipboard.PrimarySelection;

/// [PrimarySelection] over SDL3 — forwarding, for [Sdl3Clipboard]'s reason: the
/// string SDL allocates is copied and freed on the native side, and what crosses
/// into `:core` is a `String`.
///
/// Made only where the video driver has a primary selection of its own
/// ([Sdl3Backend#hasPrimarySelection(String)]). SDL would answer these calls on
/// any driver, from a buffer inside this process, and a selection nobody else
/// can paste is not what this interface promises (ADR-0504).
///
/// Confined to the UI thread, like the rest of this backend.
final class Sdl3PrimarySelection implements PrimarySelection {

    private final SdlClipboard clipboard = SdlClipboard.get();

    @Override
    public boolean hasText() {
        return clipboard.hasPrimaryText();
    }

    @Override
    public String text() {
        return clipboard.primaryText();
    }

    @Override
    public boolean text(String text) {
        return clipboard.primaryText(text);
    }

    @Override
    public String toString() {
        return "PrimarySelection[sdl3]";
    }
}
