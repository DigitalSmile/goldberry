/// Character references, resolved once on the way in.
///
/// Shared by both content halves and exported to neither: Markdown and HTML spell
/// `&amp;` the same way, and md4c's 2125-name table is the one copy of it either of
/// them needs.
///
/// `@NullMarked` puts the package under NullAway: every type is non-null unless it
/// says `@Nullable`, and the build fails on a violation.
///
/// Read more: [Markdown, HTML and the web](https://goldberry.dev/docs/components/content.html).
@NullMarked
package dev.goldberry.content.entity;

import org.jspecify.annotations.NullMarked;
