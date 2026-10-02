/// What this build of `libgoldberry`'s platform layer can do: a bit mask the library
/// reports about itself, asked once and readable before any backend has started.
///
/// A fact about the artifact rather than a probe of the running system. Exported to
/// `:core` alone, which translates it into the toolkit's own vocabulary; an
/// application reads `Goldberry.capabilities()` and never this.
///
/// Marked for NullAway, so a parameter that may be null says so on its signature.
///
/// Read more: [What this build can do](https://goldberry.dev/docs/guide/logging.html#what-this-build-can-do).
@NullMarked
package dev.goldberry.natives.platform;

import org.jspecify.annotations.NullMarked;
