package dev.goldberry.natives.sdl.gpu;

/// What a render pass does with its target before it draws.
///
/// Read more: [The native boundary](https://goldberry.dev/docs/overview/architecture.html#the-native-boundary).
public sealed interface SdlGpuLoad {

    /// Keeps what the target holds: drawing over it.
    record Keep() implements SdlGpuLoad {}

    /// Clears the target to a colour, in the format's components: for `UNORM`,
    /// 0 to 1.
    ///
    /// @param red   red
    /// @param green green
    /// @param blue  blue
    /// @param alpha alpha
    record Clear(float red, float green, float blue, float alpha) implements SdlGpuLoad {}

    /// The target's contents are undefined: every pixel will be drawn.
    record DontCare() implements SdlGpuLoad {}

    /// Keeps what the target holds.
    static SdlGpuLoad keep() {
        return new Keep();
    }

    /// Clears to a colour.
    static SdlGpuLoad clear(float red, float green, float blue, float alpha) {
        return new Clear(red, green, blue, alpha);
    }
}
