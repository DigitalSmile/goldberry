package dev.goldberry.render.window;

/// How long a window asks for the user's attention: what
/// [dev.goldberry.Window#requestAttention] is told.
///
/// What the user sees is the platform's: a bounce of the dock icon on macOS, a
/// flashing taskbar button on Windows, and on X11 the urgency hint, which the
/// desktop shows its own way. A focused window asks for nothing.
///
/// Read more: [Asking for attention](https://goldberry.dev/docs/guide/windows.html#asking-for-attention).
public enum Attention {
    /// Once: a single bounce, a brief flash.
    BRIEFLY,

    /// Until the window has the keyboard again: the dock icon keeps bouncing,
    /// the taskbar button stays lit.
    UNTIL_FOCUSED
}
