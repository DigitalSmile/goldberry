/// Reading a stylesheet: the CSS Syntax Level 3 tokenizer, the parser for the
/// supported subset, and the error that stops both.
///
/// Two modes, by whose sheet it is. The toolkit's own sheets are parsed
/// [dev.goldberry.css.parse.ParseMode#STRICT]: an unsupported construct is
/// refused with a line and column, because a rule in them that matched nothing
/// would be a control drawn wrong everywhere. An application's sheets are parsed
/// [dev.goldberry.css.parse.ParseMode#LENIENT]: the rule asking for it is dropped
/// with one warning naming it, and the application still starts. A malformed sheet
/// is refused in both, and hot reload is the caller that catches that refusal and
/// keeps the last good sheet.
///
/// One of the CSS engine's stages, each its own exported package.
///
/// Null-marked: every reference is non-null unless annotated `@Nullable`.
///
/// Read more: [Styling](https://goldberry.dev/docs/guide/styling.html).
@NullMarked
package dev.goldberry.css.parse;

import org.jspecify.annotations.NullMarked;
