package dev.goldberry.camera.backend;

import java.util.List;
import java.util.function.Consumer;

import dev.goldberry.camera.CameraFormat;
import dev.goldberry.camera.CameraInfo;

/// What `Cameras` lists and opens through: SDL on the desktop, a fake in a
/// test.
///
/// An open backend keeps the system's camera subsystem up; closing it does not
/// close the streams it opened, which hold it themselves.
public interface CameraBackend extends AutoCloseable {

    /// The cameras there are now.
    List<CameraInfo> devices();

    /// Opens camera `id` in `mode`: an uncompressed mode's frames converted to
    /// NV12, a compressed one's lent as the camera's Motion JPEG.
    ///
    /// @throws CameraLostException when the camera cannot be opened
    CameraStream open(int id, CameraFormat mode);

    /// Tells `listener` about cameras coming and going and permissions
    /// answered, on whatever thread the backend hears it, until the
    /// subscription is closed.
    AutoCloseable watch(Consumer<DeviceEvent> listener);

    @Override
    void close();
}
