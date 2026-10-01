/// The bridge from a native library's own logging to SLF4J.
///
/// Two types and no dependencies beyond SLF4J itself, which is what puts them
/// here rather than in `:natives`: GLib's handler and SDL's output function are
/// FFM bindings and belong to the native layer, but the *destination* they route
/// to is the toolkit's ordinary logging, and `:natives` is below `:core` rather
/// than above it (ADR-0174).
///
/// A package of its own beside
/// [dev.goldberry.log.Logs]
/// rather than alongside it, because the two answer different questions. `Logs`
/// is where Goldberry's **own** loggers come from; this is where somebody
/// **else's** messages arrive. An application configuring the first names
/// `dev.goldberry.*`, and the second `native.*`.
///
/// **`@NullMarked`**, like its parent — see that package's note for why this is
/// done one package at a time.
@NullMarked
package dev.goldberry.log.bridge;

import org.jspecify.annotations.NullMarked;
