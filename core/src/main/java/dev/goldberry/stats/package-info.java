/// What a window's frame loop has been costing: a window of recent frames for a
/// HUD, and the totals a run reports at exit.
///
/// [dev.goldberry.stats.FrameStats] is what a widget or a test reads, through
/// `Host.frames()`. [dev.goldberry.stats.FrameRing] is the window's own
/// implementation of it, and [dev.goldberry.stats.FrameSummary] and
/// [dev.goldberry.stats.PresentSummary] are what the launcher logs when the run
/// ends. Everything here is confined to the UI thread, like the frame loop that
/// feeds it.
///
/// The package is null-marked: a parameter or return is non-null unless
/// annotated `@Nullable`.
///
/// Read more: [What a frame costs](https://goldberry.dev/docs/performance/index.html)
/// and [Measuring](https://goldberry.dev/docs/performance/measuring.html#per-frame-timings-at-trace).
@NullMarked
package dev.goldberry.stats;

import org.jspecify.annotations.NullMarked;
