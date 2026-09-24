package io.github.digitalsmile.goldberry.gpu;

/// What a render pass draws into.
///
/// A texture made with [TextureUsage#COLOR_TARGET] today. A composited window's
/// swapchain joins it with `docs/gpu-plan.md`'s phase 3, which is why this is an
/// interface: code that draws into a target need not know which it is.
public sealed interface RenderTarget permits GpuTexture {

    /// Its format.
    TextureFormat format();

    /// Its width in pixels.
    int width();

    /// Its height in pixels.
    int height();
}
