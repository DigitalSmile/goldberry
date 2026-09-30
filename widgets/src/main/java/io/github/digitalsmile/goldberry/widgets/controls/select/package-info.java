/// `docs/core-widgets.md` §3's `select` — a closed control that shows the chosen
/// value and opens a list of choices under it.
///
/// [io.github.digitalsmile.goldberry.widgets.controls.select.Select] is `segmented`'s
/// model with a popup instead of a bar: its rows are
/// [io.github.digitalsmile.goldberry.widgets.controls.option.Option]s, and it reads
/// its value through `bind` and reports a pick through `change`. The field, the
/// value, the chevron and a `select multiple`'s chips are parts. The open list is not
/// here; it is the `select-list` part in `…controls.selectlist`, shared with
/// `text-input`'s autocomplete.
///
/// Marked for NullAway (`docs/testing.md` §2, ADR-0497).
@NullMarked
package io.github.digitalsmile.goldberry.widgets.controls.select;

import org.jspecify.annotations.NullMarked;
