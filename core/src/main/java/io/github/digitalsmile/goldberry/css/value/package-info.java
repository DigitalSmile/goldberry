/// The values a declaration resolves to — colours, lengths, transforms, shadows —
/// as plain Java with no native handle.
///
/// Each is read from its CSS spelling once, here, into the form the painter, the
/// layout engine and hit testing consume. The transform matrix is Java rather than
/// Blend2D's because hit testing needs its inverse on the input path, with no
/// rendering context in reach (ADR-0054), and two inverses that must agree exactly
/// are one too many. Colours interpolate in OKLCH, so a transition between two
/// accents does not pass through grey.
///
/// One of the CSS engine's stages, each its own exported package (ADR-0172).
///
/// Marked for NullAway (`docs/testing.md` §2, ADR-0497).
@NullMarked
package io.github.digitalsmile.goldberry.css.value;

import org.jspecify.annotations.NullMarked;
