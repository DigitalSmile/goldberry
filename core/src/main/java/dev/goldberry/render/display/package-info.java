/// The displays connected to the desktop, and the rule for putting a window on
/// one of them.
///
/// `Display` is one display: its name, its bounds and usable bounds in the
/// desktop's coordinates, and its scale. `DisplayLayout` is the set of them, and
/// where a window opens and how it is clamped back onto a display when the one
/// it was remembered on has gone. Exported to every module.
///
/// Null-marked: a parameter or return is non-null unless it says `@Nullable`.
///
/// Read more: [Displays](https://goldberry.dev/docs/guide/windows.html#displays).
@NullMarked
package dev.goldberry.render.display;

import org.jspecify.annotations.NullMarked;
