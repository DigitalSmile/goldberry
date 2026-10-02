/// Readers for the tables of an OpenType font file: the table directory, the
/// `COLR` and `CPAL` tables a colour glyph is described in, and the `glyf`
/// outlines a colour glyph clips its paint to.
///
/// The shaper and the rasterizer read a font for themselves; these readers answer
/// the questions neither of them does, which is what a colour emoji looks like.
/// Each is Java rather than a native binding because the tables are flat arrays
/// that fit on a page, and a reader with no native memory in it has nothing to
/// close. Every reader answers "nothing here" for a table it cannot read, so a
/// malformed face draws as plain outlines rather than failing to open.
///
/// The package is null-marked: a parameter or return is non-null unless annotated
/// `@Nullable`.
///
/// Read more: [Emoji](https://goldberry.dev/docs/guide/text.html#emoji).
@NullMarked
package dev.goldberry.text.font.sfnt;

import org.jspecify.annotations.NullMarked;
