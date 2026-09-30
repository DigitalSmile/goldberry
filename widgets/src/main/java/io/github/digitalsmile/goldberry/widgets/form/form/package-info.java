/// `docs/core-widgets.md` §4's `form` — a set of `field`s anywhere in its subtree
/// that validate and submit together.
///
/// [io.github.digitalsmile.goldberry.widgets.form.form.Form] gates its `submit`
/// action on every field passing, visited or not.
/// [io.github.digitalsmile.goldberry.widgets.form.form.FormController] is the handle
/// a Save button outside the form holds, and
/// [io.github.digitalsmile.goldberry.widgets.form.form.FormAccess] is the narrow seam
/// through which a field joins and leaves the form above it.
///
/// Marked for NullAway (`docs/testing.md` §2, ADR-0497).
@NullMarked
package io.github.digitalsmile.goldberry.widgets.form.form;

import org.jspecify.annotations.NullMarked;
