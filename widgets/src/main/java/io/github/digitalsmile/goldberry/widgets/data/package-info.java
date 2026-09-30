/// The vocabulary every chart shares: what a series is, how it is coloured, and
/// everything about a chart that is not its numbers (`docs/charts.md` §3.1).
///
/// [io.github.digitalsmile.goldberry.widgets.data.Series] is one named line of values
/// and [io.github.digitalsmile.goldberry.widgets.data.SeriesPalette] the theme's
/// eight slots it is coloured from, assigned in order (ADR-0194).
/// [io.github.digitalsmile.goldberry.widgets.data.ChartOptions] gathers the rest:
/// [io.github.digitalsmile.goldberry.widgets.data.Bounds],
/// [io.github.digitalsmile.goldberry.widgets.data.Curve],
/// [io.github.digitalsmile.goldberry.widgets.data.Fill],
/// [io.github.digitalsmile.goldberry.widgets.data.Markers],
/// [io.github.digitalsmile.goldberry.widgets.data.NullPolicy],
/// [io.github.digitalsmile.goldberry.widgets.data.Threshold],
/// [io.github.digitalsmile.goldberry.widgets.data.TimeAxis], a
/// [io.github.digitalsmile.goldberry.widgets.data.CrosshairGroup] shared with other
/// charts, and a [io.github.digitalsmile.goldberry.widgets.data.ChartStatus] for a
/// chart that is waiting for its data or could not get it.
/// [io.github.digitalsmile.goldberry.widgets.data.ChartSpec] and
/// [io.github.digitalsmile.goldberry.widgets.data.ChartParts] are what the chart
/// widgets have in common.
///
/// The widgets themselves are one per subpackage — `sparkline`, `line-chart`,
/// `area-chart`, `bar-chart`, `donut-chart` — all drawn on `canvas` with the theme's
/// palette rather than a chart engine; the arithmetic they share is in
/// `…widgets.data.plot`.
///
/// Marked for NullAway (`docs/testing.md` §2, ADR-0497).
@NullMarked
package io.github.digitalsmile.goldberry.widgets.data;

import org.jspecify.annotations.NullMarked;
