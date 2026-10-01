package dev.goldberry.stats;

import java.util.Locale;

/// What a whole run's composited presents cost, in one line: [FrameSummary]'s
/// companion for a window that presented through the GPU
/// (`docs/gpu-plan.md`, phase 3; ADR-0479).
///
/// Its upload figures are what say whether damage tracking pays on the GPU
/// path: a run that only blinked a caret should upload a caret's bytes a frame,
/// and one that animated the whole window the whole window.
///
/// @param frames            how many GPU presents carried new pixels: a frame with
///                          nothing new is still on the GPU, and is counted by
///                          [PresentationTally] instead
/// @param meanUploadMillis  the mean upload: staging the damage and recording it
/// @param meanUploadBytes   the mean bytes a frame uploaded
/// @param meanAcquireMillis the mean wait for the swapchain: the display's pacing
/// @param meanSubmitMillis  the mean time recording the composite and submitting
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
    /// `Locale.ROOT`, for [FrameSummary#describe]'s reason.
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
