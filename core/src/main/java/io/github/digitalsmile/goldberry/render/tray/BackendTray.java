package io.github.digitalsmile.goldberry.render.tray;

import io.github.digitalsmile.goldberry.render.PixelBuffer;

/// An icon this application has put in the desktop's notification area.
///
/// The two setters are the whole of what changes without rebuilding: the icon,
/// because a theme switch is a different picture and the tray sits on the
/// *desktop's* background rather than on the toolkit's, and the tooltip, because
/// it is a sentence about state. **The menu is not among them** — its rows are
/// platform objects created once, and replacing one would mean removing and
/// re-inserting entries the shell may have open. A tray whose menu changed is
/// closed and opened again, which is what a declarative caller does anyway.
///
/// UI-thread confined, like every other platform handle here, and must be closed.
public interface BackendTray extends AutoCloseable {

    /// Replaces the icon. Null asks for the platform's default.
    void icon(PixelBuffer icon);

    /// Replaces the hover text. Null removes it, where the platform has one.
    void tooltip(String tooltip);

    /// Whether this tray has been taken down.
    boolean isClosed();

    /// Removes the icon and its menu. Idempotent.
    @Override
    void close();
}
