/// What the desktop is set to, in the toolkit's own words: whether the system theme
/// is light or dark.
///
/// Above the backend SPI, so an application reads a setting rather than an SDL
/// enum: the `:natives` types stay inside their module, and this is the value
/// that crosses instead. A desktop that says nothing is an empty `Optional`, not
/// a third value. Exported to every module.
///
/// Null-marked: a parameter or return is non-null unless it says `@Nullable`.
///
/// Read more: [Windows, popups and the host](https://goldberry.dev/docs/guide/windows.html#the-desktops-theme).
@NullMarked
package dev.goldberry.render.desktop;

import org.jspecify.annotations.NullMarked;
