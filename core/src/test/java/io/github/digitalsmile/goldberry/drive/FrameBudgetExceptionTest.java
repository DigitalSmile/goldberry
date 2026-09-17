package io.github.digitalsmile.goldberry.drive;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import io.github.digitalsmile.goldberry.stats.FrameSummary;

class FrameBudgetExceptionTest {

    @Test
    @DisplayName("the message carries the budget and the whole summary")
    void message() {
        var summary = new FrameSummary(300, 7, 1.5, 9.25, 60);
        var failure = new FrameBudgetException(summary, 5);

        assertEquals(summary, failure.summary());
        assertEquals(5, failure.budget());
        assertTrue(failure.getMessage().contains("budget of 5"), failure.getMessage());
        assertTrue(failure.getMessage().contains(summary.describe()), failure.getMessage());
    }
}
