/// Splitting a string into the runs each face shapes: the words in the family
/// the cascade chose, the emoji in the emoji face, and the characters that
/// family has no glyph for in a fallback face that has them.
///
/// A paragraph hands its text to the [dev.goldberry.text.itemize.Itemizer] before
/// shaping it. The itemizer reads Unicode's emoji properties out of the JDK and
/// returns [dev.goldberry.text.itemize.TextRun]s, each tagged with the
/// [dev.goldberry.text.itemize.Slot] that shapes it; it decides from the text
/// alone, and whether a face exists for a slot is the font book's question.
/// Its second split, by coverage, keeps grapheme clusters whole and asks a
/// [dev.goldberry.text.itemize.FaceChoice] which faces have the characters,
/// returning [dev.goldberry.text.itemize.FaceRun]s.
///
/// The package is null-marked: a parameter or return is non-null unless annotated
/// `@Nullable`.
///
/// Read more: [Emoji](https://goldberry.dev/docs/guide/text.html#emoji).
@NullMarked
package dev.goldberry.text.itemize;

import org.jspecify.annotations.NullMarked;
