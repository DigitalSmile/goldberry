package io.github.digitalsmile.goldberry.drive;

import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import io.github.digitalsmile.goldberry.stats.FrameSummary;

class FrameBudgetExceptionTest {

    /// The workflow that reads a failed run greps [FrameSummary#describe()]
    /// (ADR-0342), and this is the path it arrives by: a budget failure that
    /// summarised the run in its own words would be invisible to it.
    @Test
    @DisplayName("the message carries the whole summary the workflow greps")
    void messageCarriesTheSummary() {
        var summary = new FrameSummary(300, 7, 1.5, 9.25, 60);

        var failure = new FrameBudgetException(summary, 5);

        assertTrue(failure.getMessage().contains(summary.describe()), failure.getMessage());
    }
}
