package dev.goldberry.mic.backend;

import java.io.Serial;

/// A recording's device can no longer record: it went away, or the system
/// took it.
public final class CaptureLostException extends RuntimeException {

    @Serial
    private static final long serialVersionUID = 1L;

    /// A loss, for `reason`.
    public CaptureLostException(String reason, Throwable cause) {
        super(reason, cause);
    }
}
