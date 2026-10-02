/// The `line-chart` — a trend with axes, one line per series — and the parts
/// every axis chart is drawn with.
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
/// Every reference is non-null unless it says `@Nullable`.
///
/// Read more: [Charts](https://goldberry.dev/docs/components/charts.html#line-chart).
@NullMarked
package dev.goldberry.widgets.data.linechart;

import org.jspecify.annotations.NullMarked;
