package dev.goldberry.camera;

/// Where a camera faces.
///
/// Desktop cameras almost always say [#UNKNOWN]; phones and some laptops say
/// front or back.
///
/// Read more: [Media](https://goldberry.dev/docs/components/media.html).
public enum CameraPosition {

    /// The system does not say, which is what a USB camera does.
    UNKNOWN,

    /// Facing the user: a picture a view usually mirrors.
    FRONT,

    /// Facing away from the user.
    BACK
}
