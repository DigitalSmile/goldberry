/// The plain values a file dialog is described and answered with.
///
/// A filter is two strings, an outcome is three cases and a kind is one of three
/// C functions — none of it touches foreign memory, so it lives here rather than
/// beside [io.github.digitalsmile.goldberry.natives.sdl.SdlFileDialogs], which
/// holds the arena and the upcall stub (ADR-0287).
package io.github.digitalsmile.goldberry.natives.sdl.dialog;
