package dev.goldberry.natives.sdl;

import org.junit.jupiter.api.Tag;

/// [SdlTrayLiveTest], on the JVM's first thread.
///
/// Cocoa's status bar belongs to the process's first thread, and the ordinary
/// test lane is not on it. The `gpu` lane is, on macOS, so this runs there: the
/// tag names the lane, not anything about a GPU. Elsewhere it repeats the live
/// tests in that lane, where a tray is not required and they skip without one.
@Tag("gpu")
class SdlTrayFirstThreadTest extends SdlTrayLiveTest {}
