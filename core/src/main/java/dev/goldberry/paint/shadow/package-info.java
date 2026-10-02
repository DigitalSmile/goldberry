/// Shadows: the arithmetic that turns a `box-shadow` into a stack of hard-edged
/// rounded-rectangle fills.
///
/// The rasterizer has no blur, so a blurred edge is drawn as nested bands, each a
/// little smaller and a little more opaque than the last. `ShadowRamp` says how
/// opaque each band must be and `ShadowGeometry` says what shape it is; the painter
/// in `paint` fills them. Neither type touches a frame, so both are tested on their
/// numbers alone.
///
/// Every type here is non-null unless it says `@Nullable`.
///
/// Read more: [Styling](https://goldberry.dev/docs/guide/styling.html#border-outline-and-shadow).
@NullMarked
package dev.goldberry.paint.shadow;

import org.jspecify.annotations.NullMarked;
