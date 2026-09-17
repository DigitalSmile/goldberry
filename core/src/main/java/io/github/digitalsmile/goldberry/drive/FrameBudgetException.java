package io.github.digitalsmile.goldberry.drive;

import io.github.digitalsmile.goldberry.stats.FrameSummary;

/// A run missed more refreshes than `--late-budget=N` allowed.
///
/// Thrown out of the launcher **after** the window has closed and the backend
/// has been released, so a run over budget is a process that exits non-zero
/// with its summary in the message and nothing left open behind it. That is
/// what turns "N of 300 frames were late while resizing" from a line in a log
/// into a red job ([ADR-0342]).
public final class FrameBudgetException extends RuntimeException {

    private final transient FrameSummary summary;
    private final long budget;

    public FrameBudgetException(FrameSummary summary, long budget) {
        super("over the late-frame budget of " + budget + ": " + summary.describe());
        this.summary = summary;
        this.budget = budget;
    }

    /// What the run measured.
    public FrameSummary summary() {
        return summary;
    }

    /// What it was allowed.
    public long budget() {
        return budget;
    }
}
