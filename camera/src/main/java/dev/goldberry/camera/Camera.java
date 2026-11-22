package dev.goldberry.camera;

import java.util.function.Consumer;

import dev.goldberry.camera.synthetic.SyntheticCamera;
import dev.goldberry.media.picture.PictureSource;

/// A camera taking pictures: a [PictureSource] of NV12 planes, newest first,
/// which a `camera-view` or any `picture-view` draws.
///
/// ```java
/// try (var cameras = Cameras.open();
///         var camera = cameras.open(CameraSpec.CALL)) {
///     return new CameraView(camera);
/// }
/// ```
///
/// A picture is borrowed as every source's is: its bytes stay as they are
/// until two more pictures have been shown after it. Listeners to
/// [#onPicture] and [#onStatus] are told on the camera's own thread.
///
/// Two kinds: a device the system lists, opened by [Cameras], and a
/// [SyntheticCamera] that paints a test pattern when it is advanced.
///
/// Read more: [Media](https://goldberry.dev/docs/components/media.html).
public sealed interface Camera extends PictureSource, AutoCloseable permits DeviceCamera, SyntheticCamera {

    /// The camera this is.
    CameraInfo info();

    /// The mode it was opened in.
    CameraFormat format();

    /// Where it is now.
    CameraStatus status();

    /// Tells `listener` each time the status changes, until the subscription
    /// is closed.
    ///
    /// @return what removes the listener
    AutoCloseable onStatus(Consumer<? super CameraStatus> listener);

    /// Stops taking pictures and lets go of the device. Idempotent; a camera
    /// lost or denied stays so.
    @Override
    void close();
}
