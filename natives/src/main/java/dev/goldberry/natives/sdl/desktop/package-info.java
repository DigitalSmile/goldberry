/// What SDL3 offers of the desktop beyond windows: the clipboard, mouse cursors, the
/// system theme, and the tray — an icon in the notification area and the menu the
/// shell draws for it.
///
/// Exported to every module. File dialogs are not here: a dialog is modal to a
/// window, and a window's pointer does not leave `…natives.sdl`.
///
/// Marked for NullAway, so a parameter that may be null says so on its signature.
///
/// Read more: [The native boundary](https://goldberry.dev/docs/overview/architecture.html#the-native-boundary).
@NullMarked
package dev.goldberry.natives.sdl.desktop;

import org.jspecify.annotations.NullMarked;
