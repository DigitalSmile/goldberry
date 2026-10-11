/// Bidirectional text: the base direction a paragraph is given, and the
/// embedding levels the Unicode bidirectional algorithm resolves under it.
///
/// A paragraph with right-to-left script in it is split into runs, one per
/// level, each shaped in its own direction. [BidiLevels] answers which run a
/// character is in and, for one line, in which order the runs are placed from
/// left to right. [Directionality] is where the paragraph's base direction comes
/// from: the box's `direction`, the first strong character
/// (`unicode-bidi: plaintext`), or every character forced one way
/// (`unicode-bidi: bidi-override`).
///
/// Measurement stays in logical order: a width is the same whichever way its
/// runs are placed, so wrapping is unchanged, and only the paint, the caret and
/// the hit test read the visual order.
///
/// The package is null-marked: a parameter or return is non-null unless annotated
/// `@Nullable`.
///
/// Read more: [Text, fonts and icons](https://goldberry.dev/docs/guide/text.html#paragraphs).
@NullMarked
package dev.goldberry.text.bidi;

import org.jspecify.annotations.NullMarked;
