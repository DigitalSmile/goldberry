package dev.goldberry.media.platform.windows;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Assumptions;

/// What a test that needs Media Foundation does without it.
///
/// Skips on any other system, and fails where `-Pgoldberry.platform.required=true`
/// says it must be there: a Windows job that skips its platform decoders is a
/// green tick over nothing. While the decoders are held back, without
/// `-Dgoldberry.media.mediaFoundation=true`, there is nothing to require, and it
/// skips.
final class WindowsRequirement {

    /// The switch that turns a skip into a failure.
    static final String REQUIRED_PROPERTY = "goldberry.platform.required";

    private WindowsRequirement() {}

    /// Media Foundation, bound, or a skip.
    static MediaFoundation enforce() {
        var mf = MediaFoundation.get();
        if (mf.isPresent()) {
            return mf.get();
        }
        var message = "Media Foundation is not available: "
                + MediaFoundation.unavailableReason().orElse("unknown reason");
        // Held back, it is not there to require: the switch is off unless asked for.
        if (Boolean.getBoolean(REQUIRED_PROPERTY)
                && MediaFoundation.enabled()
                && MediaFoundation.isWindows(System.getProperty("os.name", ""))) {
            Assertions.fail(message);
        }
        Assumptions.abort(message);
        throw new AssertionError("unreachable");
    }
}
