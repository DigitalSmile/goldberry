/// Text with marks on it: bold, italic, code, a link, a mention, and kinds an
/// application adds.
///
/// [MarkedText] is the value, a string and its [Mark]s in normal form, and it
/// says how marks move when the text under them is edited. [MarkedEdit] is the
/// same value with a caret and a selection, the marked twin of `TextEdit`. It
/// toggles formatting, steps over atoms and decides what a typed letter looks
/// like. [MarkKind] says what a mark is and how it behaves while edited; how it
/// looks is the stylesheet's.
///
/// Offsets are `char`s, as everywhere in the text stack, so a mark converts one
/// to one to an entity counted in UTF-16 code units.
///
/// The package is null-marked: a parameter or return is non-null unless annotated
/// `@Nullable`.
///
/// Read more: [Selection and editing](https://goldberry.dev/docs/guide/text.html#selection-and-editing).
@NullMarked
package dev.goldberry.text.mark;

import org.jspecify.annotations.NullMarked;
