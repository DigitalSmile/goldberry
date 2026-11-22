package dev.goldberry.camera;

import java.io.Serial;

import org.jspecify.annotations.Nullable;

/// A camera could not be listed or opened.
///
/// Read more: [Media](https://goldberry.dev/docs/components/media.html).
public final class CameraException extends RuntimeException {

    @Serial
    private static final long serialVersionUID = 1L;

    public CameraException(String message) {
        super(message);
    }

    public CameraException(String message, @Nullable Throwable cause) {
        super(message, cause);
    }
}
