package dev.goldberry.natives.desktop.shortcuts;

/// Hears a bound hotkey go down and come up.
///
/// Called on the backend's own thread (on macOS, the main thread), once per
/// press and once per release: a held key's repeats are not passed on, and a
/// release comes only after a press.
public interface HotkeyListener {

    /// The hotkey `id` went down.
    void pressed(String id);

    /// The hotkey `id` came up.
    void released(String id);
}
