/// The application's menu bar where the platform has one of its own — macOS,
/// at the top of the screen — in the toolkit's own words.
///
/// `AppMenuItem` is one row; `BackendMenuBar` is the backend SPI that shows
/// them, `NONE` everywhere but macOS; `MacMenuBarProjection` is the macOS one,
/// and `KeyEquivalent` how an accelerator becomes AppKit's. Exported to every
/// module, because the `menubar` widget is translated into these.
///
/// Null-marked: a parameter or return is non-null unless it says `@Nullable`.
///
/// Read more: [Menus and the tray](https://goldberry.dev/docs/components/menus.html#the-macos-menu-bar).
@NullMarked
package dev.goldberry.render.desktop.menubar;

import org.jspecify.annotations.NullMarked;
