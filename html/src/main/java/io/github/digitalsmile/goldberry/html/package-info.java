/// HTML in: the parser, and the document it produces.
///
/// [io.github.digitalsmile.goldberry.html.Html] is the whole surface — a `parse` and
/// the model beside it — and its own documentation carries the list of what this is
/// not, which is the important half for anybody arriving expecting a browser
/// (ADR-0298).
///
/// **The other direction lives elsewhere.** Markdown *out* as HTML is
/// [io.github.digitalsmile.goldberry.markdown.html.MarkdownHtml], a text transform
/// with no window under it, and the dependency between the two halves runs one way:
/// Markdown produces HTML and never reads it, which is why they are two packages and
/// not one (ADR-0295).
///
/// `@NullMarked` puts the package under NullAway: every type is non-null unless it
/// says `@Nullable`, and the build fails on a violation (`docs/testing.md` §2).
@NullMarked
package io.github.digitalsmile.goldberry.html;

import org.jspecify.annotations.NullMarked;
