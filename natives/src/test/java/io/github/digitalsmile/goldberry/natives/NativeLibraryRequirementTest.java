package io.github.digitalsmile.goldberry.natives;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.digitalsmile.goldberry.natives.NativeLibraryRequirement.Fail;
import io.github.digitalsmile.goldberry.natives.NativeLibraryRequirement.Run;
import io.github.digitalsmile.goldberry.natives.NativeLibraryRequirement.Skip;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

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
        assertInstanceOf(
                Run.class, NativeLibraryRequirement.decide(true, required, "/tmp/libgoldberry.so"));
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

    @ParameterizedTest
    @ValueSource(strings = {"", "   "})
    @DisplayName("a blank path says so rather than trailing an empty string")
    void blankPathIsDescribed(String path) {
        var decision = NativeLibraryRequirement.decide(false, false, path);

        assertTrue(assertInstanceOf(Skip.class, decision).reason().contains("no path was configured"));
    }

    @Test
    @DisplayName("a null path is a blank path, not a NullPointerException")
    void nullPathIsDescribed() {
        var decision = NativeLibraryRequirement.decide(false, true, null);

        assertTrue(assertInstanceOf(Fail.class, decision).reason().contains("no path was configured"));
    }

    @Test
    @DisplayName("the required-property name is the one the build sets")
    void propertyNameMatchesTheBuild() {
        // natives/build.gradle sets this on the test JVM; a rename on one side
        // only would silently restore the skip-in-CI behaviour ADR-0016 forbids.
        assertEquals("goldberry.native.required", NativeLibraryRequirement.REQUIRED_PROPERTY);
    }
}
