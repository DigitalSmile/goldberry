/// The arithmetic between a series and a plot: scales, tick placement on linear,
/// logarithmic and time axes, downsampling (LTTB), gaps, and curve
/// interpolation.
///
/// Deterministic, with no widget in it: every chart in `…widgets.data` shares
/// it and none owns it, which is why it is a package of its own.
///
/// Every reference is non-null unless it says `@Nullable`.
///
/// Read more: [Charts](https://goldberry.dev/docs/components/charts.html#what-the-five-share).
@NullMarked
package dev.goldberry.widgets.data.plot;

import org.jspecify.annotations.NullMarked;
