/// Runs of text and the links in them.
///
/// [dev.goldberry.widgets.text.Text] is `text`, a run set in one of the type
/// scale's ranks, each a [dev.goldberry.widgets.text.TextRank].
/// [dev.goldberry.widgets.text.RichText] is `rich-text`, one paragraph of
/// [dev.goldberry.widgets.text.Run]s, each `run` styled by the cascade and the
/// whole wrapping as one text.
/// [dev.goldberry.widgets.text.Link] is `link`: text that runs an action or
/// opens a URL through the desktop, whose visited state is the application's
/// to set.
///
/// Null-marked: every reference is non-null unless it says `@Nullable`.
///
/// Read more: [Text and links](https://goldberry.dev/docs/components/text.html).
@NullMarked
package dev.goldberry.widgets.text;

import org.jspecify.annotations.NullMarked;
