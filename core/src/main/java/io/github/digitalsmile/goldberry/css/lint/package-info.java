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
/// ## `@NullMarked`, which it could not be when it was written
///
/// It was written marked, taken back out, and marked again.
/// [io.github.digitalsmile.goldberry.css.StyleElement] documented **three**
/// members as "or null" — `type()`, `id()` and `parent()` — and annotated none
/// of them, in a `css` package that *is* `@NullMarked`; so [StyleLint]'s probe,
/// the first implementation written inside a marked package, could not say what
/// the interface said. The interface says it now ([ADR-0413]), which is the end
/// this had to be fixed from: an unmarked package is a checker turned off, and
/// turning one off to accommodate a lie in a signature spreads the lie.
@NullMarked
package io.github.digitalsmile.goldberry.css.lint;

import org.jspecify.annotations.NullMarked;
