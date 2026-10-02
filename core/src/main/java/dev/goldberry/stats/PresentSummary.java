package dev.goldberry.stats;

import java.util.Locale;

/// What a whole run's GPU presents cost, in one line: [FrameSummary]'s
/// companion for a window that was composited through the GPU.
///
/// [FrameStats#presentSummary] gives it, and the launcher logs [#describe] at
/// exit when anything was composited. The upload figures say whether damage
/// tracking is paying on the GPU path: a run that only blinked a caret should
/// upload a caret's worth of bytes a frame, and one that animated the whole
/// window should upload the whole window.
///
/// Read more: [Measuring](https://goldberry.dev/docs/performance/measuring.html#the-showcase-under-load).
///
/// @param frames            how many GPU presents carried new pixels. A frame
///                          with nothing new is still on the GPU, and is
///                          counted by [PresentationTally] instead
/// @param meanUploadMillis  the mean time staging the damage and recording its
///                          copy to the GPU
/// @param meanUploadBytes   the mean bytes a frame uploaded
/// @param meanAcquireMillis the mean wait for the swapchain, which is the
///                          display pacing the loop rather than work
/// @param meanSubmitMillis  the mean time recording the composite and
///                          submitting it
public record PresentSummary(
        long frames,
        double meanUploadMillis,
        double meanUploadBytes,
        double meanAcquireMillis,
        double meanSubmitMillis) {

    /// A run that composited nothing.
    public static final PresentSummary NONE = new PresentSummary(0, 0, 0, 0, 0);

    /// The line the launcher logs at exit, when anything was composited.
    ///
    /// Formatted in `Locale.ROOT`, like [FrameSummary#describe], so a workflow
    /// can grep it on any runner.
    public String describe() {
        return String.format(
                Locale.ROOT,
                "%d GPU present(s) with new pixels; upload mean %.2f ms, %.0f bytes; acquire mean %.2f ms; submit mean %.2f ms",
                frames,
                meanUploadMillis,
                meanUploadBytes,
                meanAcquireMillis,
                meanSubmitMillis);
    }
}
