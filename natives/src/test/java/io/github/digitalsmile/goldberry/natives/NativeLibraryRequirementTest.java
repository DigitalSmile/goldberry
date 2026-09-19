package io.github.digitalsmile.goldberry.natives;

import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;

import io.github.digitalsmile.goldberry.natives.NativeLibraryRequirement.Fail;
import io.github.digitalsmile.goldberry.natives.NativeLibraryRequirement.Run;
import io.github.digitalsmile.goldberry.natives.NativeLibraryRequirement.Skip;

/// The rule that decides whether a missing `libgoldberry` is a skip or a failure.
///
/// Worth its own test because the CI run of 2026-08-15 failed on exactly the
/// wrong side of it: the verify jobs could not have verified anything, and the
/// alternative to failing was passing while checking nothing (ADR-0016).
class NativeLibraryRequirementTest {

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    @DisplayName("an available library runs the test whether or not it was required")
    void availableAlwaysRuns(boolean required) {
        assertInstanceOf(Run.class, NativeLibraryRequirement.decide(true, required, "/tmp/libgoldberry.so"));
    }

    @Test
    @DisplayName("absent and optional skips, so a Java-only contributor is not blocked")
    void absentAndOptionalSkips() {
        var decision = NativeLibraryRequirement.decide(false, false, "/tmp/libgoldberry.so");

        var skip = assertInstanceOf(Skip.class, decision);
        assertTrue(
                skip.reason().contains("/tmp/libgoldberry.so"),
                () -> "the path is the first thing you want to see: " + skip.reason());
    }

    @Test
    @DisplayName("absent where required fails, rather than reporting a check that never ran")
    void absentAndRequiredFails() {
        var decision = NativeLibraryRequirement.decide(false, true, "/tmp/libgoldberry.so");

        var fail = assertInstanceOf(Fail.class, decision);
        assertTrue(
                fail.reason().contains("/tmp/libgoldberry.so"),
                () -> "the path is the first thing you want to see: " + fail.reason());
    }

    /// Null and blank are the same thing to this rule, and both sides of the
    /// required flag have to say so rather than trailing an empty string.
    @ParameterizedTest(name = "[{0}]")
    @NullSource
    @ValueSource(strings = {"", "   "})
    @DisplayName("a path that is not a path says so, whether it is required or not")
    void blankPathIsDescribed(String path) {
        assertTrue(
                assertInstanceOf(Skip.class, NativeLibraryRequirement.decide(false, false, path))
                        .reason()
                        .contains("no path was configured"),
                () -> "optional, path=" + path);
        assertTrue(
                assertInstanceOf(Fail.class, NativeLibraryRequirement.decide(false, true, path))
                        .reason()
                        .contains("no path was configured"),
                () -> "required, path=" + path);
    }
}
