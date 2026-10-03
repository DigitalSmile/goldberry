package dev.goldberry.junit;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.opentest4j.AssertionFailedError;

@DisplayName("TimeBudget")
class TimeBudgetTest {

    private static final Duration TWO_SECONDS = Duration.ofSeconds(2);
    private static final Duration FIVE_SECONDS = Duration.ofSeconds(5);

    @Test
    @DisplayName("allows the bound at no slack, and the bound widened at more")
    void widensByTheSlack() {
        var budget = TimeBudget.of(TWO_SECONDS);
        assertAll(
                () -> assertEquals(TWO_SECONDS, budget.allowed(1)),
                () -> assertEquals(Duration.ofSeconds(3), budget.allowed(1.5)),
                () -> assertEquals(Duration.ofSeconds(20), budget.allowed(10)));
    }

    @Test
    @DisplayName("never widens as far as the defect it guards against")
    void staysShortOfTheDefect() {
        var budget = TimeBudget.of(TWO_SECONDS).shortOf(FIVE_SECONDS);
        assertAll(
                () -> assertEquals(Duration.ofSeconds(4), budget.allowed(2)),
                () -> assertEquals(Duration.ofMillis(4_750), budget.allowed(3)),
                () -> assertTrue(budget.allowed(3).compareTo(FIVE_SECONDS) < 0, "slack 3 reached the defect"),
                () -> assertTrue(budget.allowed(100).compareTo(FIVE_SECONDS) < 0, "slack 100 reached the defect"));
    }

    @Test
    @DisplayName("refuses a bound that is not shorter than its defect, and one that is not positive")
    void refusesNonsense() {
        assertAll(
                () -> assertThrows(
                        IllegalArgumentException.class,
                        () -> TimeBudget.of(FIVE_SECONDS).shortOf(TWO_SECONDS)),
                () -> assertThrows(
                        IllegalArgumentException.class,
                        () -> TimeBudget.of(TWO_SECONDS).shortOf(TWO_SECONDS)),
                () -> assertThrows(IllegalArgumentException.class, () -> TimeBudget.of(Duration.ZERO)),
                () -> assertThrows(IllegalArgumentException.class, () -> TimeBudget.of(Duration.ofMillis(-1))));
    }

    @Test
    @DisplayName("keeps a microsecond budget whole: the margin under the defect is a share of it")
    void microseconds() {
        var budget = TimeBudget.of(Duration.ofNanos(400_000)).shortOf(Duration.ofMillis(1));
        assertAll(
                () -> assertEquals(Duration.ofNanos(400_000), budget.allowed(1)),
                () -> assertEquals(Duration.ofNanos(950_000), budget.allowed(10)));
    }

    @Test
    @DisplayName("refuses a bound within the last twentieth of its defect")
    void boundWellShortOfTheDefect() {
        assertThrows(
                IllegalArgumentException.class,
                () -> TimeBudget.of(Duration.ofMillis(980)).shortOf(Duration.ofSeconds(1)));
    }

    @Test
    @DisplayName("reads no slack as one")
    void unsetIsOne() {
        assertAll(
                () -> assertEquals(1, TimeBudget.slack(null)),
                () -> assertEquals(1, TimeBudget.slack(" ")),
                () -> assertEquals(2.5, TimeBudget.slack(" 2.5 ")));
    }

    @ParameterizedTest(name = "\"{0}\" is refused")
    @ValueSource(strings = {"0.5", "0", "-2", "NaN", "Infinity", "lots"})
    @DisplayName("refuses slack that would tighten a bound or is not a number")
    void slackIsRoom(String value) {
        assertThrows(IllegalArgumentException.class, () -> TimeBudget.slack(value));
    }

    @Test
    @DisplayName("admits what is within the allowance and fails a test over it, naming both")
    void assertsAgainstTheAllowance() {
        var budget = TimeBudget.of(Duration.ofMillis(100));
        assertTrue(budget.admits(Duration.ofMillis(100)));
        assertFalse(budget.admits(Duration.ofSeconds(100)));
        budget.assertWithin(Duration.ofMillis(10), "a quick thing");
        var failure = assertThrows(
                AssertionFailedError.class, () -> budget.assertWithin(Duration.ofSeconds(100), "a slow thing"));
        assertTrue(failure.getMessage().contains("a slow thing: took 100000 ms"), failure.getMessage());
    }

    @Test
    @DisplayName("puts a polling loop's deadline the allowance after its start")
    void deadline() {
        var budget = TimeBudget.of(Duration.ofMillis(250)).shortOf(Duration.ofSeconds(1));
        assertEquals(1_000L + budget.allowed().toNanos(), budget.deadlineFrom(1_000L));
    }
}
