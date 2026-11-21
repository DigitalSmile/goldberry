/// Shortcuts that work while the application is not focused:
/// [GlobalShortcuts][dev.goldberry.input.global.GlobalShortcuts] asks the
/// desktop for them and hears each one go down and come up, which is what
/// push-to-talk needs.
///
/// The GlobalShortcuts portal on Wayland, a key grab on X11, `RegisterHotKey`
/// on Windows and Carbon hotkeys on macOS, each over a library the system
/// already has. [GlobalShortcuts#simulated()][dev.goldberry.input.global.GlobalShortcuts#simulated()]
/// stands in for them in a test.
///
/// `@NullMarked`, which puts this package under NullAway.
///
/// Read more: [Input and focus](https://goldberry.dev/docs/guide/input.html#accelerators).
@NullMarked
package dev.goldberry.input.global;

import org.jspecify.annotations.NullMarked;
