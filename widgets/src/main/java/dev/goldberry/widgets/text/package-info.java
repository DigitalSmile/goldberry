/// `docs/core-widgets.md` §2's `widget.text` — runs of text and the links in them.
///
/// [dev.goldberry.widgets.text.Text] is `text`, a run set in one
/// of the type scale's ranks, each a
/// [dev.goldberry.widgets.text.TextRank] (ADR-0381).
/// [dev.goldberry.widgets.text.Link] is `link`: text that runs an
/// action or opens a URL, whose visited state is the application's to set.
///
/// Marked for NullAway (`docs/testing.md` §2, ADR-0497).
@NullMarked
package dev.goldberry.widgets.text;

import org.jspecify.annotations.NullMarked;
