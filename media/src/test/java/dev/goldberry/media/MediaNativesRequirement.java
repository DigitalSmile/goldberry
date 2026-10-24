package dev.goldberry.media;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Assumptions;

import dev.goldberry.media.ffm.MediaNatives;

/// What a test that needs `libgoldberry-media` does when it is not there.
///
/// Skips on a contributor's machine that has not run `:media:nativesBuild`, and
/// fails where `-Pgoldberry.media.required=true` says the library must be
/// there: a verification job that skips is a green tick over nothing.
public final class MediaNativesRequirement {

    /// The switch that turns a skip into a failure.
    public static final String REQUIRED_PROPERTY = "goldberry.media.required";

    private MediaNativesRequirement() {}

    /// Returns normally when the library loaded and passed its checks.
    public static void enforce() {
        var reason = MediaNatives.unavailableReason();
        if (reason.isEmpty()) {
            return;
        }
        var message = "libgoldberry-media is not available to :media's tests: " + reason.get()
                + ". Run ./gradlew :media:nativesBuild, or pass -Pgoldberry.media.libdir=<dir>.";
        if (Boolean.getBoolean(REQUIRED_PROPERTY)) {
            Assertions.fail(message);
        }
        Assumptions.abort(message);
    }
}
