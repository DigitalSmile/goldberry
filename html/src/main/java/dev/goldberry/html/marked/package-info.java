/// Marked text as HTML and back: [MarkedHtml], and the `text/html` clipboard
/// format a `rich-input` installs so that a paste from a browser keeps its
/// formatting.
///
/// `@NullMarked` puts the package under NullAway: every type is non-null unless it
/// says `@Nullable`, and the build fails on a violation.
///
/// Read more: [Markdown, HTML and the web](https://goldberry.dev/docs/components/content.html).
@NullMarked
package dev.goldberry.html.marked;

import org.jspecify.annotations.NullMarked;
