/// Marked text as Markdown and back: [MarkedMarkdown].
///
/// What a composer that edits marks sends to a service that speaks Markdown,
/// and how Markdown typed by hand becomes marks.
///
/// `@NullMarked` puts the package under NullAway: every type is non-null unless it
/// says `@Nullable`, and the build fails on a violation.
///
/// Read more: [Markdown, HTML and the web](https://goldberry.dev/docs/components/content.html).
@NullMarked
package dev.goldberry.markdown.marked;

import org.jspecify.annotations.NullMarked;
