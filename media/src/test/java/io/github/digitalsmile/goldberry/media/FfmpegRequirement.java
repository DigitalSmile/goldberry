package io.github.digitalsmile.goldberry.media;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Assumptions;

import io.github.digitalsmile.goldberry.media.ffi.FfmpegLibraries;

/// What a test that needs FFmpeg does when it is not there.
///
/// Skips on a contributor's machine that has not run `:media:ffmpegBuild`, and
/// fails where `-Pgoldberry.media.required=true` says FFmpeg must be there: a
/// verification job that skips is a green tick over nothing (ADR-0016).
public final class FfmpegRequirement {

    /// The switch that turns a skip into a failure.
    public static final String REQUIRED_PROPERTY = "goldberry.media.required";

    private FfmpegRequirement() {}

    /// Returns normally when FFmpeg loaded and passed its checks.
    public static void enforce() {
        var reason = FfmpegLibraries.unavailableReason();
        if (reason.isEmpty()) {
            return;
        }
        var message = "FFmpeg is not available to :media's tests: " + reason.get()
                + ". Run ./gradlew :media:ffmpegBuild, or pass -Pgoldberry.media.libdir=<dir>.";
        if (Boolean.getBoolean(REQUIRED_PROPERTY)) {
            Assertions.fail(message);
        }
        Assumptions.abort(message);
    }
}
