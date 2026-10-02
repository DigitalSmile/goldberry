/// HarfBuzz, the text shaper: owning wrappers over its fonts and buffers, the glyph
/// run a shaping pass produces, and the package-private binding class behind them.
///
/// Exported to `:core` alone. A shaped font decides which glyphs to draw and where;
/// drawing them is Blend2D's, over the same bytes, and the positions the two
/// exchange are in font design units. A shaping buffer is meant to be
/// reused, because a layout pass reshapes a paragraph at every width it tries.
///
/// Marked for NullAway, so a parameter that may be null says so on its signature.
///
/// Read more: [The native boundary](https://goldberry.dev/docs/overview/architecture.html#the-native-boundary).
@NullMarked
package dev.goldberry.natives.harfbuzz;

import org.jspecify.annotations.NullMarked;
