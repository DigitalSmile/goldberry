package io.github.digitalsmile.goldberry.backend;

import java.util.Objects;

/// What to create a window with.
///
/// Sizes are **logical** — the application asks for 1280×720 and gets that many
/// logical pixels whatever the display scale is. The backend resolves the
/// physical size, because only it knows which monitor the window landed on.
///
/// Decorations default to server-side on every platform (`docs/ARCHITECTURE.md`
/// §4); client-side decorations are an opt-in theme feature, not a backend
/// choice.
public record WindowSpec(String title, LogicalSize size, boolean resizable, boolean decorated) {

    public WindowSpec {
        Objects.requireNonNull(title, "title");
        Objects.requireNonNull(size, "size");
        if (size.isEmpty()) {
            throw new IllegalArgumentException("a window needs a non-empty size, got " + size);
        }
    }

    /// A resizable, server-side-decorated window — what almost everything wants.
    public static WindowSpec of(String title, LogicalSize size) {
        return new WindowSpec(title, size, true, true);
    }

    public WindowSpec withResizable(boolean value) {
        return new WindowSpec(title, size, value, decorated);
    }

    public WindowSpec withDecorated(boolean value) {
        return new WindowSpec(title, size, resizable, value);
    }
}
