/// AppKit's half of a drag out on macOS: `GoldberryDraggingSource`, a class of
/// this process's own that is the `NSDraggingSource` and the pasteboard item's
/// data provider, and the dragging items and picture it is begun with.
///
/// Not exported. [CocoaDragSource][dev.goldberry.natives.desktop.dragout.CocoaDragSource]
/// is what `:core` starts a drag with, and it puts its own words on what crosses.
///
/// Read more: [The native boundary](https://goldberry.dev/docs/overview/architecture.html#the-native-boundary).
@NullMarked
package dev.goldberry.natives.desktop.dragout.cocoa;

import org.jspecify.annotations.NullMarked;
