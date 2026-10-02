/// The `field` widget — one labelled control, with the required marker beside
/// it and the reason it is invalid under it.
///
/// [dev.goldberry.widgets.form.field.Field] finds the bound
/// control inside it and validates its value against a
/// [dev.goldberry.widgets.form.Validator] on blur and on submit.
/// The label, the control slot and the message are parts.
/// [dev.goldberry.widgets.form.field.Validated] is the four
/// questions a `form` asks of a field, exported so that the two can live in separate
/// packages and still keep their parts to themselves.
///
/// Null-marked: every reference is non-null unless annotated otherwise.
///
/// Read more: [Fields and forms](https://goldberry.dev/docs/components/forms.html#field).
@NullMarked
package dev.goldberry.widgets.form.field;

import org.jspecify.annotations.NullMarked;
