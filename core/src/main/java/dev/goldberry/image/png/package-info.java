/// Writing a PNG, in `java.base`.
///
/// Its own package beside `image` rather than a static method on
/// [Image][dev.goldberry.image.Image], for the reason
/// `paint.geom` is its own package: this is an *algorithm over a value* — chunks,
/// a CRC and a Deflater — worth reading and testing without an image in front of
/// it, and worth being obviously replaceable if a second format ever ships.
/// Exported to applications; `Image.encodePng()` is the usual way in.
///
/// `@NullMarked`, which puts this package under NullAway.
///
/// Read more: [Text, fonts and icons](https://goldberry.dev/docs/guide/text.html#images).
@NullMarked
package dev.goldberry.image.png;

import org.jspecify.annotations.NullMarked;
