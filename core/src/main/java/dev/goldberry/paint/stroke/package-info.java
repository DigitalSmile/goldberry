/// The pen: a stroke's width, its caps and joins, and its dash pattern.
///
/// Values with no native handle, so an application that draws a chevron names a
/// cap and a join rather than a rasterizer constant. A `paint.Frame` maps them to
/// the rasterizer's own in one private switch.
///
/// Every type here is non-null unless it says `@Nullable`.
///
/// Read more: [Canvas, images and QR codes](https://goldberry.dev/docs/components/drawing.html#the-painter).
@NullMarked
package dev.goldberry.paint.stroke;

import org.jspecify.annotations.NullMarked;
