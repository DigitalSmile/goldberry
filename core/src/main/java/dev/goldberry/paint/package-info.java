/// Drawing: the frame a window paints into, the values drawn on it — paths,
/// gradients, clips, offscreen layers — and the box painter where Yoga's layout
/// meets Blend2D's rasterizer.
///
/// Coordinates are logical everywhere; the frame is scaled once when it begins, so
/// a fractional position reaches the rasterizer intact. This is also the one place
/// an application is handed the rasterizer: a `canvas`'s `Painter` draws in its own
/// translated, clipped coordinates. Glyphs reach the frame through `GlyphPen`, which
/// owns the font and the staged buffer so that no `:natives` type appears in a
/// signature here.
///
/// Algorithms over these values that need no frame live in subpackages —
/// `paint.geom`, `paint.shadow`, `paint.cull`, `paint.overflow` and `paint.stroke`
/// — and the retained render tree in `paint.tree`.
///
/// Every type here is non-null unless it says `@Nullable`.
///
/// Read more: [Architecture](https://goldberry.dev/docs/overview/architecture.html#the-three-trees)
/// and [Keeping frames cheap](https://goldberry.dev/docs/performance/frames.html).
@NullMarked
package dev.goldberry.paint;

import org.jspecify.annotations.NullMarked;
