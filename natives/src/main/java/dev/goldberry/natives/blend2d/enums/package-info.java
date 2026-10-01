/// Blend2D's enumerations: composition operators, fill rules, pixel formats,
/// gradient and stroke shapes, glyph placement types and the rest. They are tables
/// of C constants that touch no foreign memory, each checked against the compiled
/// library by the layout verifier.
///
/// Split from the wrappers in `…natives.blend2d`, which hold the handles (ADR-0172).
/// Exported to `:core` alone, with the wrappers.
///
/// Marked for NullAway (`docs/testing.md` §2, ADR-0497).
@NullMarked
package dev.goldberry.natives.blend2d.enums;

import org.jspecify.annotations.NullMarked;
