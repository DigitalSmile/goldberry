package dev.goldberry.render.window;

/// Whether a second window belongs to the one that opened it.
///
/// Only a window opened through [dev.goldberry.Host#openWindow] has an owner
/// to belong to; [dev.goldberry.Window#open] refuses anything but [#NONE].
///
/// Read more: [More than one window](https://goldberry.dev/docs/guide/windows.html#more-than-one-window).
public enum Ownership {
    /// A window of its own: it has a taskbar entry and goes behind the window
    /// that opened it like any other.
    NONE,

    /// Kept above the window that opened it, and minimized with it — a tool
    /// palette, a settings window.
    OWNED,

    /// [#OWNED], and the window that opened it takes no input until this one
    /// closes — a dialog in a window of its own. A press on the blocked window
    /// brings this one to the front.
    MODAL
}
