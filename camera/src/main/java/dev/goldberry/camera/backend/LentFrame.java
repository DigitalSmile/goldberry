package dev.goldberry.camera.backend;

/// One frame a camera lends for the length of a call: planes SDL converted to
/// NV12, or a Motion JPEG frame as the camera compressed it.
public sealed interface LentFrame permits Nv12Frame, JpegFrame {

    /// When the camera took it, in the system's clock, or zero where it does
    /// not say.
    long timestampNanos();
}
