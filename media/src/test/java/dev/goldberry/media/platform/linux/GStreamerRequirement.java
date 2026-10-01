package dev.goldberry.media.platform.linux;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Assumptions;

/// What a test that needs GStreamer does without it.
///
/// Skips on any other system, and fails on Linux where
/// `-Pgoldberry.platform.required=true` says the decoders must be there: a Linux
/// job that skips its platform decoders is a green tick over nothing (ADR-0016).
/// The flag is a job's, and a macOS or Windows job sets it for its own system's.
final class GStreamerRequirement {

    /// The switch that turns a skip into a failure.
    static final String REQUIRED_PROPERTY = "goldberry.platform.required";

    private GStreamerRequirement() {}

    /// GStreamer, bound, or a skip.
    static GStreamer enforce() {
        var gstreamer = GStreamer.get();
        if (gstreamer.isPresent()) {
            return gstreamer.get();
        }
        var message =
                "GStreamer is not available: " + GStreamer.unavailableReason().orElse("unknown reason");
        if (Boolean.getBoolean(REQUIRED_PROPERTY) && GStreamer.isLinux(System.getProperty("os.name", ""))) {
            Assertions.fail(message);
        }
        Assumptions.abort(message);
        throw new AssertionError("unreachable");
    }
}
