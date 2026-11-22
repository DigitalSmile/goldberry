package dev.goldberry.camera.backend;

import java.io.Serial;

import org.jspecify.annotations.Nullable;

/// A camera could not be opened, or stopped.
public final class CameraLostException extends RuntimeException {

    @Serial
    private static final long serialVersionUID = 1L;

    public CameraLostException(String message) {
        super(message);
    }

    public CameraLostException(String message, @Nullable Throwable cause) {
        super(message, cause);
    }
}
