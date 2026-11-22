package dev.goldberry.camera.backend;

/// One opened camera, as a backend hands it over.
///
/// [#take] belongs to one thread, the camera's capture thread; the frame it
/// lends is readable only during the call.
public interface CameraStream extends AutoCloseable {

    /// Whether the user has let the camera be used.
    Permission permission();

    /// Hands the newest frame to `sink`, if one has arrived since the last
    /// call, and gives it back to the camera when `sink` returns. A frame that
    /// `sink` cannot use is the sink's to drop: it is given back all the same.
    ///
    /// @return whether there was a frame
    /// @throws CameraLostException when the camera stopped
    boolean take(FrameSink sink);

    @Override
    void close();

    /// What the user said about the camera.
    enum Permission {
        /// Not asked yet, or not answered.
        PENDING,
        /// Allowed.
        APPROVED,
        /// Refused.
        DENIED
    }

    /// Reads one lent frame.
    @FunctionalInterface
    interface FrameSink {

        /// `frame`, valid for the call: NV12 planes, or a Motion JPEG frame
        /// when the camera was opened in a compressed mode.
        void accept(LentFrame frame);
    }
}
