/// What the desktop is set to, in the toolkit's own words: whether the system theme
/// is light or dark.
///
/// Above the backend SPI, so an application reads a setting rather than an SDL
/// enum — ADR-0174 keeps `natives.*` inside `:natives`, and this is the value that
/// crosses instead. A desktop that says nothing is an empty `Optional`, not a third
/// value.
///
/// Marked for NullAway (`docs/testing.md` §2, ADR-0497).
@NullMarked
package dev.goldberry.render.desktop;

import org.jspecify.annotations.NullMarked;
