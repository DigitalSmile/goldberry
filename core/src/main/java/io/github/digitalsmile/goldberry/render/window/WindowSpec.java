package io.github.digitalsmile.goldberry.render.window;

import java.util.Objects;

import io.github.digitalsmile.goldberry.render.model.LogicalSize;

/// What to create a window with.
///
/// Sizes are **logical** — the application asks for 1280×720 and gets that many
/// logical pixels whatever the display scale is. The backend resolves the
/// physical size, because only it knows which monitor the window landed on.
///
/// Decorations default to server-side on every platform (`docs/ARCHITECTURE.md`
/// §4); client-side decorations are an opt-in theme feature, not a backend
/// choice.
///
/// [#maximized] is the odd one out and deliberately so: it is a *state* the
/// desktop owns rather than a property of the window, so it is asked for at
/// creation and never read back here. See
/// [ADR-0221](../../../../../../book/src/adr/0221-a-window-may-open-maximized.md).
///
/// @param size      the size the window is created at, and the one it returns to
///                  when a [#maximized] window is restored
/// @param maximized whether it opens filling the work area
public record WindowSpec(String title, LogicalSize size, boolean resizable, boolean decorated, boolean maximized) {

    public WindowSpec {
        Objects.requireNonNull(title, "title");
        Objects.requireNonNull(size, "size");
        if (size.isEmpty()) {
            throw new IllegalArgumentException("a window needs a non-empty size, got " + size);
        }
        if (maximized && !resizable) {
            // Not a warning, because the two readings of it are opposite: SDL
            // silently drops the flag, so the application gets a small window it
            // asked to have filled -- and a user cannot fix that from the
            // desktop either, a fixed-size window having no maximize button.
            throw new IllegalArgumentException("a window that cannot be resized has no maximized state to open in");
        }
    }

    /// A resizable, server-side-decorated window — what almost everything wants.
    public static WindowSpec of(String title, LogicalSize size) {
        return new WindowSpec(title, size, true, true, false);
    }

    public WindowSpec withResizable(boolean value) {
        return new WindowSpec(title, size, value, decorated, maximized);
    }

    public WindowSpec withDecorated(boolean value) {
        return new WindowSpec(title, size, resizable, value, maximized);
    }

    /// Opens filling the work area, keeping [#size] as the restored size.
    public WindowSpec withMaximized(boolean value) {
        return new WindowSpec(title, size, resizable, decorated, value);
    }
}
