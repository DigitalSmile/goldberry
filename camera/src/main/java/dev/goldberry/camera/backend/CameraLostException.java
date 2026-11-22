package dev.goldberry.camera.backend;

import org.jspecify.annotations.Nullable;

/// A camera could not be opened, or stopped.
public final class CameraLostException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public CameraLostException(String message) {
        super(message);
    }

    public CameraLostException(String message, @Nullable Throwable cause) {
        super(message, cause);
    }
}
