/// `docs/core-widgets.md` §5's `statistic` — a labelled number, with an optional unit
/// and a delta that says which way it moved.
///
/// [dev.goldberry.widgets.panel.statistic.Statistic] takes its
/// value as a string, because formatting is the application's: a number formatted
/// inside the toolkit would make a golden image that depends on the machine's locale.
/// The value, label, unit and delta are parts.
///
/// Marked for NullAway (`docs/testing.md` §2, ADR-0497).
@NullMarked
package dev.goldberry.widgets.panel.statistic;

import org.jspecify.annotations.NullMarked;
