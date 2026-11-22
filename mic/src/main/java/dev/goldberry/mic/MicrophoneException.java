package dev.goldberry.mic;

import java.io.Serial;

import org.jspecify.annotations.Nullable;

/// A microphone could not be listed or opened: no audio driver, a device that
/// went away between the list and the open, a format it refuses.
///
/// Read more: [Media](https://goldberry.dev/docs/components/media.html).
public final class MicrophoneException extends RuntimeException {

    @Serial
    private static final long serialVersionUID = 1L;

    /// A failure `message` describes.
    public MicrophoneException(String message) {
        super(message);
    }

    /// A failure `message` describes, which `cause` led to.
    public MicrophoneException(String message, @Nullable Throwable cause) {
        super(message, cause);
    }
}
