package io.github.digitalsmile.goldberry.media.platform.macos;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Assumptions;

/// What a test that needs the macOS frameworks does without them.
///
/// Skips on any other system, and fails where `-Pgoldberry.platform.required=true`
/// says they must be there: a macOS job that skips its platform decoders is a
/// green tick over nothing (ADR-0016).
final class PlatformRequirement {

    /// The switch that turns a skip into a failure.
    static final String REQUIRED_PROPERTY = "goldberry.platform.required";

    private PlatformRequirement() {}

    /// The bound frameworks, or a skip.
    static Frameworks enforce() {
        var frameworks = Frameworks.get();
        if (frameworks.isPresent()) {
            return frameworks.get();
        }
        var message = "the macOS frameworks are not available: "
                + Frameworks.unavailableReason().orElse("unknown reason");
        if (Boolean.getBoolean(REQUIRED_PROPERTY)) {
            Assertions.fail(message);
        }
        Assumptions.abort(message);
        throw new AssertionError("unreachable");
    }
}
