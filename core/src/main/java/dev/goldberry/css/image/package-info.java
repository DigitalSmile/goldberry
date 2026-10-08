/// Pictures a stylesheet names: `url()` beside the gradients, and the
/// nine-slice `border-image` drawn from one.
///
/// A [dev.goldberry.css.image.CssImage] is what a `background-image` layer or a
/// `border-image-source` resolves to: a `url()` or a gradient. The cascade
/// carries the address, and the painter asks
/// [dev.goldberry.css.image.StyleImages] for the decoded picture once there is a
/// box to draw it in. The decode is asynchronous: until it arrives the layer
/// draws nothing, and the frame after it arrives draws it.
///
/// [dev.goldberry.css.image.BorderImage] is CSS's `border-image`, part of a
/// box's decoration, and [dev.goldberry.css.image.NineSlice] is the arithmetic
/// that cuts a picture into nine pieces and places them.
///
/// Null-marked: every reference is non-null unless annotated `@Nullable`.
///
/// Read more: [Styling](https://goldberry.dev/docs/guide/styling.html#backgrounds-and-gradients).
@NullMarked
package dev.goldberry.css.image;

import org.jspecify.annotations.NullMarked;
