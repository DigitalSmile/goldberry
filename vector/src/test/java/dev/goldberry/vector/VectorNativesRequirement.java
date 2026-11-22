package dev.goldberry.vector;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Assumptions;

import dev.goldberry.vector.thorvg.VectorNatives;

/// What a test that needs `libgoldberry-vector` does when it is not there.
///
/// Skips on a contributor's machine that has not run `:vector:nativesBuild`, and
/// fails where `-Pgoldberry.vector.required=true` says the library must be
/// there: a verification job that skips is a green tick over nothing.
public final class VectorNativesRequirement {

    /// The switch that turns a skip into a failure.
    public static final String REQUIRED_PROPERTY = "goldberry.vector.required";

    private VectorNativesRequirement() {}

    /// Returns normally when the library loaded and passed its checks.
    public static void enforce() {
        var reason = VectorNatives.unavailableReason();
        if (reason.isEmpty()) {
            return;
        }
        var message = "libgoldberry-vector is not available to :vector's tests: " + reason.get()
                + ". Run ./gradlew :vector:nativesBuild, or pass -Pgoldberry.vector.libdir=<dir>.";
        if (Boolean.getBoolean(REQUIRED_PROPERTY)) {
            Assertions.fail(message);
        }
        Assumptions.abort(message);
    }
}
