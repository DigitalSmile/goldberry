package dev.goldberry.natives.sdl.camera;

/// Whether the user has let the application use an opened camera:
/// `SDL_CameraPermissionState`.
///
/// Desktops that do not ask answer [#APPROVED] at once. macOS asks the first
/// time, and SDL reports the answer through a device event that only arrives
/// while the window loop pumps events.
///
/// Read more: [The native boundary](https://goldberry.dev/docs/overview/architecture.html#the-native-boundary).
public enum SdlCameraPermission {

    /// The user refused; the camera will never give a frame.
    DENIED(-1),

    /// The user has not answered yet.
    PENDING(0),

    /// Frames may be acquired.
    APPROVED(1);

    private final int value;

    SdlCameraPermission(int value) {
        this.value = value;
    }

    /// The C enumerator's value.
    public int value() {
        return value;
    }

    /// The name the C shim reports this constant under, for the layout probe.
    public String nativeName() {
        return "SDL_CAMERA_PERMISSION_STATE_" + name();
    }

    /// The state SDL reported as `value`. Anything SDL 3.4 does not define is
    /// read as [#PENDING]: a camera is not used on an answer nobody gave.
    public static SdlCameraPermission of(int value) {
        for (var permission : values()) {
            if (permission.value == value) {
                return permission;
            }
        }
        return PENDING;
    }
}
