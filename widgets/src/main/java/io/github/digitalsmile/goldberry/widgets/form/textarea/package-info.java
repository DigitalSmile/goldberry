/// `docs/core-widgets.md` §4's `text-area` — a multi-line text field that soft-wraps,
/// grows between a minimum and a maximum number of rows, and scrolls after that.
///
/// [io.github.digitalsmile.goldberry.widgets.form.textarea.TextArea] is `text-input`
/// with a second dimension: `Enter` inserts a newline and `Up` and `Down` move
/// between lines, on the same [io.github.digitalsmile.goldberry.text.edit.TextEdit]
/// model. For use as an editor pane it adds an optional line-number gutter and access
/// to the caret and selection. The gutter is a part here; the caret, selection and
/// text are the parts it shares with `text-input` in the unexported `…form.parts`.
///
/// Marked for NullAway (`docs/testing.md` §2, ADR-0497).
@NullMarked
package io.github.digitalsmile.goldberry.widgets.form.textarea;

import org.jspecify.annotations.NullMarked;
