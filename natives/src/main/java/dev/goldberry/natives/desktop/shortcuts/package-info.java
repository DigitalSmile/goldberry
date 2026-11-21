/// Shortcuts the desktop delivers while the application is not focused, one
/// platform at a time: the GlobalShortcuts portal over D-Bus on Wayland, a key
/// grab on the X11 root window, `RegisterHotKey` on Windows and Carbon's
/// `RegisterEventHotKey` on macOS.
///
/// Each binds a library the process already has, loaded at run time, so
/// nothing here is in the native build. Each delivers a press and a release,
/// which is what push-to-talk needs, and says why a shortcut was not granted:
/// the user refused it, another application holds it, the key has no code on
/// this platform, or there is no way to ask.
///
/// Exported to `:core` alone, which puts the toolkit's own words on it.
///
/// Read more: [The native boundary](https://goldberry.dev/docs/overview/architecture.html#the-native-boundary).
@org.jspecify.annotations.NullMarked
package dev.goldberry.natives.desktop.shortcuts;
