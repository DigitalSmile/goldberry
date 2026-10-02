/// Fields and forms: the widgets that take typed input and the `form` that
/// gathers them, with the validation model they share.
///
/// Each field lives in its own sub-package (`textinput`, `textarea`,
/// `codeinput`, `datepicker`, `timepicker`, `colorpicker`); the `field` wrapper
/// gives any of them a label and a message, and `form` collects their values and
/// validity. The pieces a field is built from are in `parts`. Every reference
/// is non-null unless it says `@Nullable`.
///
/// Read more: [Fields and forms](https://goldberry.dev/docs/components/forms.html).
@NullMarked
package dev.goldberry.widgets.form;

import org.jspecify.annotations.NullMarked;
