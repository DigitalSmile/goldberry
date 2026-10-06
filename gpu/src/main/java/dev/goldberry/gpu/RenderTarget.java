package dev.goldberry.gpu;

/// What a render pass draws into.
///
/// A texture made with [TextureUsage#COLOR_TARGET], or one level of one layer
/// of it. A composited window's swapchain is the other kind, which is why this
/// is an interface: code that draws into a target need not know which it is.
public sealed interface RenderTarget permits GpuTexture, TextureView {

    /// Its format.
    TextureFormat format();

    /// Its width in pixels.
    int width();

    /// Its height in pixels.
    int height();
}
