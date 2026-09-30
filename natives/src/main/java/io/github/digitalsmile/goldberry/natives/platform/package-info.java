/// What this build of `libgoldberry`'s platform layer can do: a bit mask the library
/// reports about itself, asked once and readable before any backend has started.
///
/// A fact about the artifact rather than a probe of the running system. Exported to
/// `:core` alone, which translates it into the toolkit's own vocabulary; an
/// application reads `Goldberry.capabilities()` and never this (ADR-0325).
///
/// Marked for NullAway (`docs/testing.md` §2, ADR-0497).
@NullMarked
package io.github.digitalsmile.goldberry.natives.platform;

import org.jspecify.annotations.NullMarked;
