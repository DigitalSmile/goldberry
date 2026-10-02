/// The `statistic`: a labelled number, with an optional unit and a delta that
/// says which way it moved.
///
/// [dev.goldberry.widgets.panel.statistic.Statistic] takes its
/// value as a string, because formatting is the application's: a number formatted
/// inside the toolkit would make a golden image that depends on the machine's locale.
/// The value, label, unit and delta are parts.
///
/// Every reference is non-null unless it says `@Nullable`.
///
/// Read more: [Panels](https://goldberry.dev/docs/components/panels.html#statistic).
@NullMarked
package dev.goldberry.widgets.panel.statistic;

import org.jspecify.annotations.NullMarked;
