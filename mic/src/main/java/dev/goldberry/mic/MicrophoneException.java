package dev.goldberry.mic;

import java.io.Serial;

/// A microphone could not be listed or opened: no audio driver, a device that
/// went away between the list and the open, a format it refuses.
///
/// Read more: [Media](https://goldberry.dev/docs/components/media.html).
public final class MicrophoneException extends RuntimeException {

    @Serial
    private static final long serialVersionUID = 1L;

    MicrophoneException(String message, Throwable cause) {
        super(message, cause);
    }
}
