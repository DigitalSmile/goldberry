/// The `select` — a closed control that shows the chosen value and opens a list
/// of choices under it.
///
/// [dev.goldberry.widgets.controls.select.Select] is `segmented`'s model with a
/// popup instead of a bar: its rows are
/// [dev.goldberry.widgets.controls.option.Option]s, and it reads its value
/// through `bind` and reports a pick through `change`. The field, the value, the
/// chevron and a `select multiple`'s chips are parts, styleable as
/// `select-field`, `select-value`, `select-chevron`, `select-chips` and
/// `select-chip`. The open list is not here; it is the `select-list` part in
/// `…controls.selectlist`, shared with `text-input`'s autocomplete.
///
/// Every reference is non-null unless it says `@Nullable`.
///
/// Read more: [Choices](https://goldberry.dev/docs/components/choices.html#select).
@NullMarked
package dev.goldberry.widgets.controls.select;

import org.jspecify.annotations.NullMarked;
