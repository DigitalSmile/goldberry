package dev.goldberry.natives.sdl.camera;

import java.util.Optional;

/// The `SDL_PixelFormat`s a camera offers that `goldberry-camera` reads or
/// names: the YUV layouts SDL's drivers report, and Motion JPEG.
///
/// SDL converts between the uncompressed ones on its camera thread when a
/// camera is opened in a format other than its own. It never decodes
/// [#MJPG]: a camera opened in it hands over compressed frames.
///
/// Read more: [The native boundary](https://goldberry.dev/docs/overview/architecture.html#the-native-boundary).
public enum SdlCameraPixelFormat {

    /// Y, then interleaved U and V: two planes, chroma at half size both ways.
    NV12(0x3231564e, false),

    /// Y, then interleaved V and U.
    NV21(0x3132564e, false),

    /// Packed Y0 U0 Y1 V0: what most USB cameras give uncompressed.
    YUY2(0x32595559, false),

    /// Packed U0 Y0 V0 Y1.
    UYVY(0x59565955, false),

    /// Packed Y0 V0 Y1 U0.
    YVYU(0x55595659, false),

    /// Y, U, V: three planes, also called I420.
    IYUV(0x56555949, false),

    /// Y, V, U: three planes.
    YV12(0x32315659, false),

    /// Motion JPEG: every frame a JPEG.
    MJPG(0x47504a4d, true);

    private final int value;
    private final boolean compressed;

    SdlCameraPixelFormat(int value, boolean compressed) {
        this.value = value;
        this.compressed = compressed;
    }

    /// The `SDL_PixelFormat` value, a FourCC.
    public int value() {
        return value;
    }

    /// Whether a frame in this format is compressed, so its bytes are not
    /// pixels.
    public boolean compressed() {
        return compressed;
    }

    /// The name the C shim reports this constant under, for the layout probe.
    public String nativeName() {
        return "SDL_PIXELFORMAT_" + name();
    }

    /// The format SDL reported as `value`, or empty for one this enum does not
    /// name (an RGB format, which some drivers offer).
    public static Optional<SdlCameraPixelFormat> of(int value) {
        for (var format : values()) {
            if (format.value == value) {
                return Optional.of(format);
            }
        }
        return Optional.empty();
    }
}
