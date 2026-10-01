/// `docs/charts.md` §3.1's `line-chart` — a trend with axes, one line per series, and
/// the parts every axis chart is drawn with.
///
/// [dev.goldberry.widgets.data.linechart.LineChart] is the widget.
/// [dev.goldberry.widgets.data.linechart.ChartSeries] and
/// [dev.goldberry.widgets.data.linechart.ChartPoint] are the
/// `series` and `point` nodes of a chart's inline data.
/// [dev.goldberry.widgets.data.linechart.ChartPlot], the canvas
/// that draws the plot and tracks the hovered point, and
/// [dev.goldberry.widgets.data.linechart.ChartLegend], present for
/// two series and absent for one, are shared with the other charts through
/// [dev.goldberry.widgets.data.ChartParts].
///
/// Marked for NullAway (`docs/testing.md` §2, ADR-0497).
@NullMarked
package dev.goldberry.widgets.data.linechart;

import org.jspecify.annotations.NullMarked;
