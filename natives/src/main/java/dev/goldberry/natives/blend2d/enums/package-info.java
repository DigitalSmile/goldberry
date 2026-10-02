/// Blend2D's enumerations: composition operators, fill rules, pixel formats,
/// gradient and stroke shapes, glyph placement types and the rest. They are tables
/// of C constants that touch no foreign memory, each checked against the compiled
/// library by the layout verifier.
///
/// Split from the wrappers in `…natives.blend2d`, which hold the handles.
/// Exported to `:core` alone, with the wrappers.
///
/// Marked for NullAway, so a parameter that may be null says so on its signature.
///
/// Read more: [The native boundary](https://goldberry.dev/docs/overview/architecture.html#the-native-boundary).
@NullMarked
package dev.goldberry.natives.blend2d.enums;

import org.jspecify.annotations.NullMarked;
