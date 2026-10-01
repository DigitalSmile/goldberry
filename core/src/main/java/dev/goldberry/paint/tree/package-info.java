/// The retained render tree — ADR-0004's third tree — kept per window between
/// frames, laid out by Yoga and painted by `paint.BoxPainter`.
///
/// Each render object owns its Yoga node and the `Box` that styles it; the paint
/// logic stays a function of the box. This is also where the toolkit's flexbox
/// vocabulary is translated into Yoga's, in one place (ADR-0279); where the CSS
/// rule Yoga lacks — an absolute box placed against its containing block's padding
/// box — is applied; and where a frame whose root overflowed is walked for the
/// boxes that did not fit.
///
/// Marked for NullAway (`docs/testing.md` §2, ADR-0497).
@NullMarked
package dev.goldberry.paint.tree;

import org.jspecify.annotations.NullMarked;
