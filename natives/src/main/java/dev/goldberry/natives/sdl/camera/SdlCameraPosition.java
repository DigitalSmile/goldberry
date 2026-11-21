package dev.goldberry.natives.sdl.camera;

/// Where a camera faces, as SDL reports it: `SDL_CameraPosition`.
///
/// Desktop cameras are almost always [#UNKNOWN]; phones and some tablets say
/// front or back. A front-facing picture is the one a view mirrors.
///
/// Read more: [The native boundary](https://goldberry.dev/docs/overview/architecture.html#the-native-boundary).
public enum SdlCameraPosition {

    /// SDL does not know, which is what a USB camera says.
    UNKNOWN(0),

    /// Facing the user.
    FRONT_FACING(1),

    /// Facing away from the user.
    BACK_FACING(2);

    private final int value;

    SdlCameraPosition(int value) {
        this.value = value;
    }

    /// The C enumerator's value.
    public int value() {
        return value;
    }

    /// The name the C shim reports this constant under, for the layout probe.
    public String nativeName() {
        return "SDL_CAMERA_POSITION_" + name();
    }

    /// The position SDL reported as `value`, or [#UNKNOWN] for one a later SDL
    /// added.
    public static SdlCameraPosition of(int value) {
        for (var position : values()) {
            if (position.value == value) {
                return position;
            }
        }
        return UNKNOWN;
    }
}
