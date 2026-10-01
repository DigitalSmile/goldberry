/// The arithmetic between a series and a plot: scales, tick placement on linear,
/// logarithmic and time axes, downsampling (LTTB), gaps, and curve
/// interpolation.
///
/// Deterministic, with no widget in it: every chart in `…widgets.data` shares
/// it and none owns it (ADR-0496).
///
/// Marked for NullAway from its first commit (`docs/testing.md` §2).
@NullMarked
package dev.goldberry.widgets.data.plot;

import org.jspecify.annotations.NullMarked;
