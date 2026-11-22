/// What this module reads of a document itself, before ThorVG parses it: the
/// root `<svg>` start tag, its `viewBox` and `preserveAspectRatio`, and the
/// document again with a colour or a size given to its root.
///
/// Not exported.
///
/// Read more: [Images](https://goldberry.dev/docs/components/drawing.html#image).
@NullMarked
package dev.goldberry.vector.document;

import org.jspecify.annotations.NullMarked;
