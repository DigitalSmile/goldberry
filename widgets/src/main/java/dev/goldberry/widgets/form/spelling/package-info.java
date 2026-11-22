/// What a text field needs to check its spelling: a check that follows the
/// field's `spellcheck` and `lang` and the application's installed checkers,
/// and the spans that draw a misspelled word as `::spelling-error` says.
///
/// Not exported. `text-input`, `text-area` and `rich-input` are its callers,
/// and the application's half is `dev.goldberry.text.spell`.
///
/// Every reference is non-null unless it says `@Nullable`.
///
/// Read more: [Fields and forms](https://goldberry.dev/docs/components/forms.html#text-input).
@NullMarked
package dev.goldberry.widgets.form.spelling;

import org.jspecify.annotations.NullMarked;
