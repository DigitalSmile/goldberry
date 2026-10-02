/// Text layout: a paragraph that is shaped once and wraps itself at any width,
/// the shaped run it is built on, and the cache that keeps the same text from
/// being shaped twice.
///
/// Shaping is HarfBuzz's and rasterizing is Blend2D's; the two libraries know
/// nothing of each other, and this package holds them to the one thing they must
/// agree on, the units a glyph position is in. A [dev.goldberry.text.Paragraph]
/// is shaped once, in the font's design units, and re-wrapped at any width over
/// the same glyphs, because layout asks for its size several times a pass. The
/// [dev.goldberry.text.ShapedRun] and [dev.goldberry.text.TextDirection] are the
/// toolkit's own values, so no native type crosses here.
///
/// Fonts, editing, long documents, itemization, line flow and OpenType tables
/// each have a subpackage.
///
/// The package is null-marked: a parameter or return is non-null unless
/// annotated `@Nullable`.
///
/// Read more: [Text, fonts and icons](https://goldberry.dev/docs/guide/text.html#paragraphs).
@NullMarked
package dev.goldberry.text;

import org.jspecify.annotations.NullMarked;
