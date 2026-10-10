/// A picture cut in nine for a painter: the cut itself, and where its nine
/// pieces go on a rectangle.
///
/// A value with no native handle, for `paint.stroke`'s reason. A
/// `paint.Frame` draws it, through the same painter the stylesheet's
/// `border-image` uses, so a plate drawn on a canvas and a panel skinned by a
/// stylesheet meet their seams alike.
///
/// Every type here is non-null unless it says `@Nullable`.
///
/// Read more: [Canvas, images and QR codes](https://goldberry.dev/docs/components/drawing.html#the-painter).
@NullMarked
package dev.goldberry.paint.slice;

import org.jspecify.annotations.NullMarked;
