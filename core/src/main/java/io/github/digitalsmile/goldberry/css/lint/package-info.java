/// Asking a stylesheet whether the engine will do what it says.
///
/// §8's subset is small on purpose and an unsupported declaration is not an
/// error, which is right for a stylesheet that is data and wrong as the only
/// signal an author ever gets: four properties were written into the toolkit's
/// own sheets, silently discarded, and found by looking at a picture.
///
/// The answer is a **value an application asks for**, not a louder log. That is
/// the finding [ADR-0216] left behind and [ADR-0243] made general: a dropped
/// value already warns, and a warning nobody reads is a warning at any level.
///
/// Its own package rather than more static methods on `css`, for ADR-0172's
/// reason: the engine's job is to resolve a style, and asking it what it *would
/// not* do is a different one with its own vocabulary ([ADR-0257]).
///
/// ## Not `@NullMarked`, and the reason is not this package
///
/// It was written marked and taken back out.
/// [io.github.digitalsmile.goldberry.css.StyleElement] documents **three**
/// members as "or null" — `type()`, `id()` and `parent()` — and annotates none
/// of them, in a `css` package that *is* `@NullMarked`. So an implementation
/// written inside a marked package cannot say what the interface's own javadoc
/// says, and `StyleLint`'s probe is the first implementation to be written in
/// one. Marking this package would mean either lying in three overrides or
/// annotating the interface, which moves every implementation and every caller.
/// Recorded in `book/src/TODO.md` rather than fixed in passing.
package io.github.digitalsmile.goldberry.css.lint;
