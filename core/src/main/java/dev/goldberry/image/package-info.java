/// Decoded images: bytes that were a PNG, a JPEG, a GIF, a WebP or a QOI, and
/// are pixels now. [Image][dev.goldberry.image.Image] is the value, and the
/// subpackages hold the codecs the toolkit writes itself.
///
/// Exported to applications. **Its own package rather than a class in `paint`**,
/// because three parts of the toolkit want the same value and only one of them
/// draws: [Frame][dev.goldberry.paint.Frame] draws one, an offscreen render
/// produces one, and a clipboard carries one. `paint` already depends on
/// `render`, so an `Image` living in `paint` would have made the clipboard
/// depend on the paint package to name the thing it holds.
///
/// `@NullMarked`, which puts this package under NullAway: every type is non-null
/// unless it says `@Nullable`, and the build fails on a violation.
///
/// Read more: [Text, fonts and icons](https://goldberry.dev/docs/guide/text.html#images).
@NullMarked
package dev.goldberry.image;

import org.jspecify.annotations.NullMarked;
