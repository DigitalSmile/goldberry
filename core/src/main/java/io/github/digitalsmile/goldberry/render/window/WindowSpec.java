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
/// ADR-0221.
///
/// [#minimumSize] is the floor the **user** may drag the window down to. It is
/// here rather than checked in a resize handler because the window manager is
/// what enforces it: the pointer stops at the edge, instead of the window
/// shrinking and springing back a frame later (ADR-0304).
///
/// @param size        the size the window is created at, and the one it returns
///                    to when a [#maximized] window is restored
/// @param minimumSize the smallest the user may make it, or a zero size for "no
///                    minimum" — the default, and the only honest one for a
///                    toolkit that does not know what the window contains
/// @param maximized   whether it opens filling the work area
public record WindowSpec(
        String title,
        LogicalSize size,
        LogicalSize minimumSize,
        boolean resizable,
        boolean decorated,
        boolean maximized) {

    /// No floor at all — what [#of] gives, and what SDL means by `0x0`.
    public static final LogicalSize NO_MINIMUM = LogicalSize.of(0, 0);

    public WindowSpec {
        Objects.requireNonNull(title, "title");
        Objects.requireNonNull(size, "size");
        Objects.requireNonNull(minimumSize, "minimumSize");
        if (size.isEmpty()) {
            throw new IllegalArgumentException("a window needs a non-empty size, got " + size);
        }
        // Refused rather than silently reconciled, and the two readings are again
        // opposite: growing the window to its floor is not what an application
        // that asked for 400x300 wanted, and opening below the floor is a window
        // the user can never get back to the size it started at.
        if (minimumSize.width() > size.width() || minimumSize.height() > size.height()) {
            throw new IllegalArgumentException("a window cannot open at " + size + ", which is smaller than the "
                    + minimumSize + " minimum it declares");
        }
        if (!minimumSize.isEmpty() && !resizable) {
            // Harmless to SDL and meaningless to a reader: a window nobody can
            // resize has no size to be stopped at.
            throw new IllegalArgumentException("a window that cannot be resized has no minimum size to stop at");
        }
        if (maximized && !resizable) {
            // Not a warning, because the two readings of it are opposite: SDL
            // silently drops the flag, so the application gets a small window it
            // asked to have filled -- and a user cannot fix that from the
            // desktop either, a fixed-size window having no maximize button.
            throw new IllegalArgumentException("a window that cannot be resized has no maximized state to open in");
        }
    }

    /// A resizable, server-side-decorated window with no minimum size — what
    /// almost everything wants.
    public static WindowSpec of(String title, LogicalSize size) {
        return new WindowSpec(title, size, NO_MINIMUM, true, true, false);
    }

    public WindowSpec withResizable(boolean value) {
        return new WindowSpec(title, size, minimumSize, value, decorated, maximized);
    }

    public WindowSpec withDecorated(boolean value) {
        return new WindowSpec(title, size, minimumSize, resizable, value, maximized);
    }

    /// Opens filling the work area, keeping [#size] as the restored size.
    public WindowSpec withMaximized(boolean value) {
        return new WindowSpec(title, size, minimumSize, resizable, decorated, value);
    }

    /// The floor the user may drag this window down to.
    ///
    /// A zero width or height is "no minimum on that axis", so a window that
    /// only cares about its width says `LogicalSize.of(480, 0)` rather than
    /// guessing a height.
    ///
    /// @throws IllegalArgumentException if it is larger than [#size] on either
    ///         axis, or if this window is not resizable
    public WindowSpec withMinimumSize(LogicalSize value) {
        return new WindowSpec(title, size, value, resizable, decorated, maximized);
    }

    /// Whether this window declares a floor at all.
    public boolean hasMinimumSize() {
        return !minimumSize.isEmpty();
    }
}
