/// The `text-input` widget — a single-line text field with selection, clipboard,
/// undo, a placeholder, a maximum length, password masking, input filters and
/// autocomplete.
///
/// [dev.goldberry.widgets.form.textinput.TextInput] owns the edit
/// and reports each new value through `change`; the editing model itself is
/// [dev.goldberry.text.edit.TextEdit] in `:core`, where a canvas can use it too.
/// [dev.goldberry.widgets.form.textinput.TextFilter] decides what
/// the field accepts, judged on the whole value an edit would produce. The caret,
/// selection and text are parts it shares with `text-area` in the unexported
/// `…form.parts`.
///
/// Null-marked: every reference is non-null unless annotated otherwise.
///
/// Read more: [Fields and forms](https://goldberry.dev/docs/components/forms.html#text-input).
@NullMarked
package dev.goldberry.widgets.form.textinput;

import org.jspecify.annotations.NullMarked;
