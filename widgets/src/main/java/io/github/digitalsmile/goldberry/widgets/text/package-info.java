/// `docs/core-widgets.md` §2's `widget.text` — runs of text and the links in them.
///
/// [io.github.digitalsmile.goldberry.widgets.text.Text] is `text`, a run set in one
/// of the type scale's ranks, each a
/// [io.github.digitalsmile.goldberry.widgets.text.TextRank] (ADR-0381).
/// [io.github.digitalsmile.goldberry.widgets.text.Link] is `link`: text that runs an
/// action or opens a URL, whose visited state is the application's to set.
///
/// Marked for NullAway (`docs/testing.md` §2, ADR-0497).
@NullMarked
package io.github.digitalsmile.goldberry.widgets.text;

import org.jspecify.annotations.NullMarked;
