/// The values a declaration resolves to — colours, lengths, transforms, shadows —
/// as plain Java with no native handle.
///
/// Each is read from its CSS spelling once, here, into the form the painter, the
/// layout engine and hit testing consume. The transform matrix is Java rather than
/// the rasterizer's because hit testing needs its inverse on the input path, with
/// no rendering context in reach, and two inverses that must agree exactly are one
/// too many. Colours interpolate in OKLCH, so a transition between two accents
/// does not pass through grey.
///
/// One of the CSS engine's stages, each its own exported package.
///
/// Null-marked: every reference is non-null unless annotated `@Nullable`.
///
/// Read more: [Styling](https://goldberry.dev/docs/guide/styling.html#properties).
@NullMarked
package dev.goldberry.css.value;

import org.jspecify.annotations.NullMarked;
