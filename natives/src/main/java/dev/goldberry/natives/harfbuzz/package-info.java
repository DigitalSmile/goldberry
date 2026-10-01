/// HarfBuzz, the text shaper: owning wrappers over its fonts and buffers, the glyph
/// run a shaping pass produces, and the package-private binding class behind them.
///
/// Exported to `:core` alone. A shaped font decides which glyphs to draw and where;
/// drawing them is Blend2D's, over the same bytes, and the positions the two
/// exchange are in font design units (ADR-0034). A shaping buffer is meant to be
/// reused, because a layout pass reshapes a paragraph at every width it tries.
///
/// Marked for NullAway (`docs/testing.md` §2, ADR-0497).
@NullMarked
package dev.goldberry.natives.harfbuzz;

import org.jspecify.annotations.NullMarked;
