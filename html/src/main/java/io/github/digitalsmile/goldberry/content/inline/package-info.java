/// A line of mixed faces, as the widgets that draw it.
///
/// The one piece of rendering `markdown-view` and `html-view` share, and the reason
/// it is here rather than in either of them: a paragraph is a wrapping row of words
/// because the text stack shapes one font per run, and that is true of a document
/// whatever parsed it (ADR-0295, ADR-0298).
///
/// Not exported. This is how the two views are built, not something an application
/// composes.
///
/// `@NullMarked` puts the package under NullAway: every type is non-null unless it
/// says `@Nullable`, and the build fails on a violation (`docs/testing.md` §2).
@NullMarked
package io.github.digitalsmile.goldberry.content.inline;

import org.jspecify.annotations.NullMarked;
