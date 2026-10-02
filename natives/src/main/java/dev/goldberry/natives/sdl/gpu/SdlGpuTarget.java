package dev.goldberry.natives.sdl.gpu;

/// What a render pass or a blit can draw into: a texture the device made, or the
/// swapchain texture of a claimed window for the current frame.
///
/// Read more: [The native boundary](https://goldberry.dev/docs/overview/architecture.html#the-native-boundary).
public sealed interface SdlGpuTarget permits SdlGpuTexture, SdlGpuSwapchainTexture {

    /// The width in pixels.
    int width();

    /// The height in pixels.
    int height();
}
