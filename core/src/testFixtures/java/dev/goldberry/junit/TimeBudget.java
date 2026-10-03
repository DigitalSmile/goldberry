package dev.goldberry.junit;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
import java.util.Objects;
import java.util.function.Supplier;

import org.jspecify.annotations.Nullable;

/// How long something may take in a test that has to read a clock, with room for
/// the machine the test happens to run on.
///
/// A cost is guarded by a count, never by a clock, and most of this repository's
/// tests do that. The few that cannot -- a pump that must return *promptly*, a
/// failure that must come *at once* rather than after a backoff -- compare a
/// measured duration with a bound, and a bound that passes on an idle laptop
/// fails on a runner building four modules beside it. So a bound here has two
/// parts:
///
/// - **the bound**, a generous multiple of what the operation takes when it
///   works, written in the test;
/// - **the defect**, what it takes when it is broken -- the timeout it was
///   supposed to beat, the backoff it was supposed to skip. The allowance may
///   grow towards it but never into its last five percent, so no amount of
///   slack lets the defect pass.
///
/// `-Dgoldberry.timing.slack=3` multiplies every bound by three, for a machine
/// known to be loaded. It is `1` when unset, and less than `1` is refused: slack
/// is room, never a tightening.
///
/// ```java
/// TimeBudget.of(Duration.ofSeconds(2)).shortOf(Duration.ofSeconds(5))
///         .assertWithin(elapsed, "the pump returned with a frame requested");
/// ```
///
/// Read more: [A clock bound has
/// room](https://goldberry.dev/docs/contributing/testing.html#a-clock-bound-has-room).
///
/// @param bound  what the operation may take on an idle machine
/// @param defect what it takes when it is broken, or `null` when there is no such
///     figure
public record TimeBudget(Duration bound, @Nullable Duration defect) {

    /// The system property that widens every bound: `-Dgoldberry.timing.slack=2`.
    public static final String SLACK_PROPERTY = "goldberry.timing.slack";

    /// How close to the defect an allowance may come, as a share of it: never
    /// within the last five percent. A share and not a duration, because a budget
    /// is as often microseconds as seconds.
    private static final double CEILING = 0.95;

    public TimeBudget {
        Objects.requireNonNull(bound, "bound");
        if (bound.isNegative() || bound.isZero()) {
            throw new IllegalArgumentException("a bound is a positive duration: " + bound);
        }
        if (defect != null && bound.compareTo(ceiling(defect)) > 0) {
            throw new IllegalArgumentException(
                    "the bound " + bound + " must be well short of the defect it guards against, " + defect);
        }
    }

    /// A bound with no defect to stay short of.
    ///
    /// @param bound what the operation may take on an idle machine
    /// @return the budget
    public static TimeBudget of(Duration bound) {
        return new TimeBudget(bound, null);
    }

    /// The same bound, whose allowance stays short of `defect` however much slack
    /// it is given.
    ///
    /// @param defect what the operation takes when it is broken
    /// @return the budget
    public TimeBudget shortOf(Duration defect) {
        return new TimeBudget(bound, Objects.requireNonNull(defect, "defect"));
    }

    /// The slack this JVM was given, `1` when [#SLACK_PROPERTY] is unset.
    ///
    /// @return a factor of at least `1`
    /// @throws IllegalArgumentException when the property is not a number of at least `1`
    public static double slack() {
        return slack(System.getProperty(SLACK_PROPERTY));
    }

    /// The slack a property value asks for.
    ///
    /// @param value the property's value, or `null`
    /// @return a factor of at least `1`
    /// @throws IllegalArgumentException when the value is not a number of at least `1`
    static double slack(@Nullable String value) {
        if (value == null || value.isBlank()) {
            return 1;
        }
        double factor;
        try {
            factor = Double.parseDouble(value.strip());
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException(SLACK_PROPERTY + " is a number, not \"" + value + "\"", e);
        }
        if (!Double.isFinite(factor) || factor < 1) {
            throw new IllegalArgumentException(SLACK_PROPERTY + " is room, a factor of at least 1, not " + value);
        }
        return factor;
    }

    /// What the operation is allowed under this JVM's slack.
    ///
    /// @return the bound, widened, and short of the defect
    public Duration allowed() {
        return allowed(slack());
    }

    /// What the operation is allowed under a given slack.
    ///
    /// @param slack a factor of at least `1`
    /// @return the bound times `slack`, never reaching the defect
    Duration allowed(double slack) {
        var widened = Duration.ofNanos(Math.round(bound.toNanos() * slack));
        return switch (defect) {
            case null -> widened;
            case Duration limit when widened.compareTo(ceiling(limit)) < 0 -> widened;
            case Duration limit -> ceiling(limit);
        };
    }

    /// The most an allowance may be when the operation breaks at `defect`.
    private static Duration ceiling(Duration defect) {
        return Duration.ofNanos((long) (defect.toNanos() * CEILING));
    }

    /// Whether a measurement is within the allowance.
    ///
    /// @param measured what the operation took
    /// @return `true` when it took no longer than [#allowed()]
    public boolean admits(Duration measured) {
        return measured.compareTo(allowed()) <= 0;
    }

    /// Fails the test when a measurement is over the allowance.
    ///
    /// @param measured what the operation took
    /// @param what     what was measured, for the message
    public void assertWithin(Duration measured, String what) {
        assertWithin(measured, () -> what);
    }

    /// Fails the test when a measurement is over the allowance.
    ///
    /// @param measured what the operation took
    /// @param what     what was measured, for the message, built only on failure
    public void assertWithin(Duration measured, Supplier<String> what) {
        var allowed = allowed();
        assertTrue(
                measured.compareTo(allowed) <= 0,
                () -> what.get() + ": took " + measured.toMillis() + " ms, allowed " + allowed.toMillis() + " ms ("
                        + bound.toMillis() + " ms at slack " + slack() + ")");
    }

    /// The `System.nanoTime()` past which a loop polling for this operation gives up.
    ///
    /// @param startNanos when the operation started, by `System.nanoTime()`
    /// @return the deadline
    public long deadlineFrom(long startNanos) {
        return startNanos + allowed().toNanos();
    }
}
