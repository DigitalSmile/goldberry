/// Yoga's style vocabulary: the enumerations behind CSS's flexbox properties, and a
/// length as a number and its unit. C constants and plain values that touch no
/// foreign memory, each enumerator checked against the compiled library.
///
/// Exported to `:core` alone. An application sees `:core`'s own layout vocabulary,
/// which is translated into this one beside the node that needs it.
///
/// Marked for NullAway (`docs/testing.md` §2, ADR-0497).
@NullMarked
package dev.goldberry.natives.yoga.style;

import org.jspecify.annotations.NullMarked;
