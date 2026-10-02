/// Asking a stylesheet whether the engine will do what it says.
///
/// [StyleLint] resolves every rule of a stylesheet through the real cascade and
/// hands every declaration to the real `ComputedStyle`, and returns a [Finding]
/// for each declaration the engine would apply nothing from, each rule that names
/// no type, and a root no sheet gives a colour. An unsupported declaration is not
/// an error, which is right for a stylesheet that is data and wrong as the only
/// signal an author gets, so the answer is a value an application asks for at
/// start-up rather than a louder log. It is its own package because the engine's
/// job is to resolve a style, and asking it what it would not do is a different
/// job with its own vocabulary.
///
/// Null-marked: every reference is non-null unless annotated `@Nullable`.
///
/// Read more:
/// [Logging and diagnostics](https://goldberry.dev/docs/guide/logging.html#failure-messages-and-what-they-mean).
@NullMarked
package dev.goldberry.css.lint;

import org.jspecify.annotations.NullMarked;
