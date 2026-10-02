/// Backgrounds: a box's colour and the gradients painted over it.
///
/// `Background` is what the cascade resolves `background`,
/// `background-color`, `background-image` and `background-position` into, and
/// what a `Box` carries to the painter. A `GradientLayer` is a gradient as the
/// stylesheet wrote it, in proportions of a box; the painter resolves it into
/// a `paint.Gradient` once the box has a size.
///
/// Every type here is non-null unless it says `@Nullable`.
///
/// Read more: [Styling](https://goldberry.dev/docs/guide/styling.html#backgrounds-and-gradients).
@NullMarked
package dev.goldberry.css.background;

import org.jspecify.annotations.NullMarked;
