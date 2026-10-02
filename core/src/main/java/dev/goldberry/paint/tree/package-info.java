/// The retained render tree — the third of the toolkit's three trees — kept per
/// window between frames, laid out by Yoga and painted by `paint.BoxPainter`.
///
/// Each render object owns its Yoga node and the `Box` that styles it; the paint
/// logic stays a function of the box. This is also where the toolkit's flexbox
/// vocabulary is translated into Yoga's, in one place; where the CSS rule Yoga
/// lacks — an absolute box placed against its containing block's padding box — is
/// applied; and where a frame whose root overflowed is walked for the boxes that
/// did not fit.
///
/// Every type here is non-null unless it says `@Nullable`.
///
/// Read more: [Architecture](https://goldberry.dev/docs/overview/architecture.html#the-three-trees).
@NullMarked
package dev.goldberry.paint.tree;

import org.jspecify.annotations.NullMarked;
