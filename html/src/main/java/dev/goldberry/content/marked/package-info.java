/// What the Markdown and HTML conversions of marked text share: the links a
/// mention and a custom kind are written as, and the builder both readers fill.
///
/// Not exported. An application converts through
/// [dev.goldberry.markdown.marked.MarkedMarkdown] and
/// [dev.goldberry.html.marked.MarkedHtml].
///
/// `@NullMarked` puts the package under NullAway: every type is non-null unless it
/// says `@Nullable`, and the build fails on a violation.
///
/// Read more: [Markdown, HTML and the web](https://goldberry.dev/docs/components/content.html).
@NullMarked
package dev.goldberry.content.marked;

import org.jspecify.annotations.NullMarked;
