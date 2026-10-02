/// The vocabulary every chart shares: what a series is, how it is coloured, and
/// everything about a chart that is not its numbers.
///
/// [dev.goldberry.widgets.data.Series] is one named line of values
/// and [dev.goldberry.widgets.data.SeriesPalette] the theme's
/// eight slots it is coloured from, assigned in order.
/// [dev.goldberry.widgets.data.ChartOptions] gathers the rest:
/// [dev.goldberry.widgets.data.Bounds],
/// [dev.goldberry.widgets.data.Curve],
/// [dev.goldberry.widgets.data.Fill],
/// [dev.goldberry.widgets.data.Markers],
/// [dev.goldberry.widgets.data.NullPolicy],
/// [dev.goldberry.widgets.data.Threshold],
/// [dev.goldberry.widgets.data.TimeAxis], a
/// [dev.goldberry.widgets.data.CrosshairGroup] shared with other
/// charts, and a [dev.goldberry.widgets.data.ChartStatus] for a
/// chart that is waiting for its data or could not get it.
/// [dev.goldberry.widgets.data.ChartSpec] and
/// [dev.goldberry.widgets.data.ChartParts] are what the chart
/// widgets have in common.
///
/// The widgets themselves are one per subpackage — `sparkline`, `line-chart`,
/// `area-chart`, `bar-chart`, `donut-chart` — all drawn on `canvas` with the theme's
/// palette rather than a chart engine; the arithmetic they share is in
/// `…widgets.data.plot`.
///
/// Every reference is non-null unless it says `@Nullable`.
///
/// Read more: [Charts](https://goldberry.dev/docs/components/charts.html).
@NullMarked
package dev.goldberry.widgets.data;

import org.jspecify.annotations.NullMarked;
