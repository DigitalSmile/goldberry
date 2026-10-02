/// The `form` widget — a set of `field`s anywhere in its subtree that validate
/// and submit together.
///
/// [dev.goldberry.widgets.form.form.Form] gates its `submit`
/// action on every field passing, visited or not.
/// [dev.goldberry.widgets.form.form.FormController] is the handle
/// a Save button outside the form holds, and
/// [dev.goldberry.widgets.form.form.FormAccess] is the narrow seam
/// through which a field joins and leaves the form above it.
///
/// Null-marked: every reference is non-null unless annotated otherwise.
///
/// Read more: [Fields and forms](https://goldberry.dev/docs/components/forms.html#form).
@NullMarked
package dev.goldberry.widgets.form.form;

import org.jspecify.annotations.NullMarked;
