package dev.goldberry.camera;

import java.util.Objects;

/// Where a [Camera] is.
///
/// Read more: [Media](https://goldberry.dev/docs/components/media.html).
public sealed interface CameraStatus {

    /// Waiting for the user to let the application use the camera: what macOS
    /// asks the first time. No pictures yet.
    record Asking() implements CameraStatus {}

    /// Taking pictures.
    record Active() implements CameraStatus {}

    /// The user did not let the application use the camera. Final.
    record Denied() implements CameraStatus {}

    /// The camera stopped: it was unplugged, or the system took it. Final.
    ///
    /// @param reason what happened, for a person to read
    record Lost(String reason) implements CameraStatus {
        public Lost {
            Objects.requireNonNull(reason, "reason");
        }
    }

    /// Closed. Final.
    record Closed() implements CameraStatus {}

    /// Whether no more pictures will come.
    default boolean isFinal() {
        return this instanceof Denied || this instanceof Lost || this instanceof Closed;
    }
}
