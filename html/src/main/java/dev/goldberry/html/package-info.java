/// HTML in: the parser, and the document it produces.
///
/// [dev.goldberry.html.Html] is the whole surface — a `parse` and
/// the model beside it — and its own documentation carries the list of what this is
/// not, which is the important half for anybody arriving expecting a browser.
///
/// **The other direction lives elsewhere.** Markdown *out* as HTML is
/// [dev.goldberry.markdown.html.MarkdownHtml], a text transform
/// with no window under it, and the dependency between the two halves runs one way:
/// Markdown produces HTML and never reads it, which is why they are two packages and
/// not one.
///
/// `@NullMarked` puts the package under NullAway: every type is non-null unless it
/// says `@Nullable`, and the build fails on a violation.
///
/// Read more: [HTML view](https://goldberry.dev/docs/components/content.html#html-view).
@NullMarked
package dev.goldberry.html;

import org.jspecify.annotations.NullMarked;
