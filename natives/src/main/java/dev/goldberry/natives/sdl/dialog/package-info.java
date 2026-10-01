/// The plain values a file dialog is described and answered with.
///
/// A filter is two strings, an outcome is three cases and a kind is one of three
/// C functions — none of it touches foreign memory, so it lives here rather than
/// beside [dev.goldberry.natives.sdl.SdlFileDialogs], which
/// holds the arena and the upcall stub (ADR-0287).
///
/// Marked for NullAway (`docs/testing.md` §2, ADR-0497).
@NullMarked
package dev.goldberry.natives.sdl.dialog;

import org.jspecify.annotations.NullMarked;
