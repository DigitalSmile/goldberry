package dev.goldberry.camera;

/// A camera plugged in or taken away, as [Cameras#onChange] tells it.
///
/// Read more: [Media](https://goldberry.dev/docs/components/media.html).
public sealed interface CameraChange {

    /// The camera that came or went.
    CameraInfo camera();

    /// Plugged in.
    record Added(CameraInfo camera) implements CameraChange {}

    /// Taken away. A camera open on it is [CameraStatus.Lost].
    record Removed(CameraInfo camera) implements CameraChange {}
}
