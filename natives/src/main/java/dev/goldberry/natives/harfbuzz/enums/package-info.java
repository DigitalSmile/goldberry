/// HarfBuzz's enumerations — text direction and memory mode. They are C constants
/// that touch no foreign memory, each checked against the compiled library.
///
/// HarfBuzz numbers some of its enums with deliberate gaps, so every value is
/// declared rather than counted from an ordinal. Exported to `:core` alone, with the
/// wrappers in `…natives.harfbuzz`.
///
/// Marked for NullAway (`docs/testing.md` §2, ADR-0497).
@NullMarked
package dev.goldberry.natives.harfbuzz.enums;

import org.jspecify.annotations.NullMarked;
