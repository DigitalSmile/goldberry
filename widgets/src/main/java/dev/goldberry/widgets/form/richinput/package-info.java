/// The `rich-input` widget: a field that takes formatting, for a message being
/// written.
///
/// [dev.goldberry.widgets.form.richinput.RichInput] holds a
/// [dev.goldberry.text.mark.MarkedText], words and the marks over them, and
/// edits it with the canvas [dev.goldberry.text.edit.Editor] that `:core` gives
/// any application. What this package adds is what makes that a control: a box,
/// a placeholder, focus, the stylesheet's looks for each set of marks (`mark`
/// nodes under the field), and growing from `rows` to `max-rows`.
///
/// Null-marked: every reference is non-null unless annotated otherwise.
///
/// Read more: [Fields and forms](https://goldberry.dev/docs/components/forms.html#keyboard).
@NullMarked
package dev.goldberry.widgets.form.richinput;

import org.jspecify.annotations.NullMarked;
