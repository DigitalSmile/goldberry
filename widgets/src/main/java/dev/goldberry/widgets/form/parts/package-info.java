/// The parts a field is built from: the caret, the preedit run of an input
/// method, the underline, the value box and the picker field that opens a
/// popup.
///
/// A part is selectable from CSS and never written in markup; a field composes
/// them. Every reference is non-null unless it says `@Nullable`.
///
/// Read more: [Fields and forms](https://goldberry.dev/docs/components/forms.html#how-a-field-talks-to-the-model).
@NullMarked
package dev.goldberry.widgets.form.parts;

import org.jspecify.annotations.NullMarked;
