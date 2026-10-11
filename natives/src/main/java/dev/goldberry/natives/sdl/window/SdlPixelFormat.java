package dev.goldberry.natives.sdl.window;

import java.util.Optional;

/// The `SDL_PixelFormat` values the CPU present path accepts for a window
/// surface.
///
/// Both are 32 bits per pixel with blue in the lowest byte on a little-endian
/// machine — which is exactly Blend2D's `BL_FORMAT_PRGB32` byte order, so a frame
/// can be copied row by row with no conversion. `XRGB8888` ignores the fourth
/// byte instead of treating it as alpha, which for opaque window contents is the
/// same picture.
///
/// Anything else means the platform handed back a surface Goldberry cannot blit
/// into, and the backend says so rather than writing a scrambled frame.
///
/// Read more: [The native boundary](https://goldberry.dev/docs/overview/architecture.html#the-native-boundary).
public enum SdlPixelFormat {
    XRGB8888(0x16161804),

    ARGB8888(0x16362004);

    private final int value;

    SdlPixelFormat(int value) {
        this.value = value;
    }

    public int value() {
        return value;
    }

    /// The name the C shim reports this constant under, for the layout probe.
    public String nativeName() {
        return "SDL_PIXELFORMAT_" + name();
    }

    /// Whether the fourth byte is alpha rather than padding: whether a window
    /// with a surface in this format can be see-through anywhere.
    public boolean hasAlpha() {
        return this == ARGB8888;
    }

    /// The constant SDL reports as `format`, or empty for one the CPU present
    /// path cannot blit into.
    public static Optional<SdlPixelFormat> of(int format) {
        for (var candidate : values()) {
            if (candidate.value == format) {
                return Optional.of(candidate);
            }
        }
        return Optional.empty();
    }

    /// Whether a surface in this format can be blitted into directly.
    public static boolean isBlittable(int format) {
        return of(format).isPresent();
    }
}
