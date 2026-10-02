/// `@NullMarked`, which puts this package under NullAway.
///
/// Inside a marked package every type is non-null unless it says `@Nullable`,
/// and the build fails on a violation. The annotation is the whole content of
/// this file: there is nothing package-specific to say about nullness, and a
/// paragraph pretending otherwise in every package would be padding.
///
/// A module written after NullAway was adopted is marked from its first commit, which
/// is the difference between opting in and catching up.
///
/// Read more: [Markdown view](https://goldberry.dev/docs/components/content.html#markdown-view).
@NullMarked
package dev.goldberry.markdown.view;

import org.jspecify.annotations.NullMarked;
