/// md4c's enumerations — block, span and text types, table cell alignment, and the
/// flags of its dialect mask. They are C constants that touch no foreign memory.
///
/// md4c has inserted enumerators into the middle of these between releases, so each
/// value is declared and checked against the compiled library (ADR-0294). Exported
/// to `:html` alone, with the parser.
///
/// Marked for NullAway (`docs/testing.md` §2, ADR-0497).
@NullMarked
package dev.goldberry.natives.md4c.enums;

import org.jspecify.annotations.NullMarked;
