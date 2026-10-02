/// Reading a stylesheet: the CSS Syntax Level 3 tokenizer, the parser for the
/// supported subset, and the error that stops both.
///
/// Strict where a browser is lenient. A browser drops what it does not understand
/// because the page was written for somebody else; a toolkit is reading a sheet its
/// own application shipped, so an unsupported construct is refused with a line and
/// column rather than left as a widget in the wrong colour. Hot reload is the one
/// caller that catches the refusal, and keeps the last good sheet.
///
/// One of the CSS engine's stages, each its own exported package.
///
/// Null-marked: every reference is non-null unless annotated `@Nullable`.
///
/// Read more: [Styling](https://goldberry.dev/docs/guide/styling.html).
@NullMarked
package dev.goldberry.css.parse;

import org.jspecify.annotations.NullMarked;
