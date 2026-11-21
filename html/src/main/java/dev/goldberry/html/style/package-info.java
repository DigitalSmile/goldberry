/// A document's own styles — a mail's `<style>` and `style=` — read, cut down to a
/// safe subset, matched against the document's own tree and handed to the view as
/// one generated class per element plus the rules those classes stand for.
///
/// Not exported: `html-view` is the way in, and what a mail may say about its
/// appearance is that widget's documentation.
///
/// `@NullMarked` puts the package under NullAway: every type is non-null unless it
/// says `@Nullable`, and the build fails on a violation.
///
/// Read more: [HTML view](https://goldberry.dev/docs/components/content.html#html-view).
@NullMarked
package dev.goldberry.html.style;

import org.jspecify.annotations.NullMarked;
