/// A parsed page, as a tree of records.
///
/// [io.github.digitalsmile.goldberry.html.model.HtmlNode] is sealed over four kinds
/// and [io.github.digitalsmile.goldberry.html.model.Element]'s tag is an open string,
/// which is the one asymmetry worth knowing about: a fold over the *kinds* is
/// exhaustive and checked by the compiler, while a tag nobody has heard of still
/// renders — as a block or an inline according to
/// [io.github.digitalsmile.goldberry.html.model.Tags], styled by its own name
/// (ADR-0298).
///
/// This is the module's real surface for HTML, in the same way
/// [io.github.digitalsmile.goldberry.markdown.model] is for Markdown: a preview is
/// not the only thing an application does with a page, and an outline, a link check,
/// a word count and an image prefetch are all walks of one parse.
///
/// `@NullMarked` puts the package under NullAway: every type is non-null unless it
/// says `@Nullable`, and the build fails on a violation (`docs/testing.md` §2).
@NullMarked
package io.github.digitalsmile.goldberry.html.model;

import org.jspecify.annotations.NullMarked;
