/// Writing a PNG, in `java.base`.
///
/// Its own package beside `image` rather than a static method on
/// [Image][io.github.digitalsmile.goldberry.image.Image], for the reason
/// `paint.geom` is its own package: this is an *algorithm over a value* — chunks,
/// a CRC and a Deflater — worth reading and testing without an image in front of
/// it, and worth being obviously replaceable if a second format ever ships
/// (ADR-0278's precedent, ADR-0283's decision).
///
/// `@NullMarked`, which puts this package under NullAway.
@NullMarked
package io.github.digitalsmile.goldberry.image.png;

import org.jspecify.annotations.NullMarked;
