/// Splitting a string into the runs each face shapes: the words in the family
/// the cascade chose, and the emoji in the emoji face.
///
/// A paragraph hands its text to the [dev.goldberry.text.itemize.Itemizer] before
/// shaping it. The itemizer reads Unicode's emoji properties out of the JDK and
/// returns [dev.goldberry.text.itemize.TextRun]s, each tagged with the
/// [dev.goldberry.text.itemize.Slot] that shapes it; it decides from the text
/// alone, and whether a face exists for a slot is the font book's question.
///
/// The package is null-marked: a parameter or return is non-null unless annotated
/// `@Nullable`.
///
/// Read more: [Emoji](https://goldberry.dev/docs/guide/text.html#emoji).
@NullMarked
package dev.goldberry.text.itemize;

import org.jspecify.annotations.NullMarked;
