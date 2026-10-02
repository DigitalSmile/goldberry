/// The value types the backend SPI measures in: logical and physical sizes,
/// points and rectangles, the scale between them, and the one pixel format a
/// frame is presented in.
///
/// Logical pixels are what layout, styling and application code work in;
/// physical pixels are what a raster is made of; `DisplayScale` is the only
/// conversion between them. Exported to every module, because a window's size
/// and a popup's position are stated in these.
///
/// Null-marked: a parameter or return is non-null unless it says `@Nullable`.
///
/// Read more: [How layout works](https://goldberry.dev/docs/layout/index.html#logical-pixels).
@NullMarked
package dev.goldberry.render.model;

import org.jspecify.annotations.NullMarked;
