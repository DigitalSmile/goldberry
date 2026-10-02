package dev.goldberry.render.composite;

import dev.goldberry.natives.sdl.SdlWindowHandle;

/// Presents windows through the GPU: `:gpu`'s, found by `ServiceLoader` the
/// first time a window is to be composited, and one per backend.
///
/// It owns the process's one GPU device, made on the first [#claim] and never
/// before, so an application that composites nothing never loads a driver. By
/// default that is the first frame of the first window, because windows present
/// through the GPU unless the policy or the driver says otherwise. A device that
/// cannot be made is remembered, and every later claim is refused at once rather
/// than tried again.
///
/// Confined to the UI thread, which on macOS is the process's first.
///
/// Read more: [Logging and diagnostics](https://goldberry.dev/docs/guide/logging.html#which-way-a-window-presents).
public interface Compositor extends AutoCloseable {

    /// Takes `window` over: from now on it presents through a swapchain, and has
    /// no window surface. The caller has given the surface up first.
    ///
    /// [Claim.Refused] when it cannot be done -- no device, or a window the
    /// driver will not claim -- with the reason. The window stays on the CPU,
    /// and nothing is left claimed.
    Claim claim(SdlWindowHandle window);

    /// A surface that renders GPU layers and reads them back, for one window
    /// that presents on the CPU: one the policy leaves there, one the GPU
    /// refused, a popup, or a headless one. Its layers are rendered to textures
    /// and read back into the frame.
    ///
    /// Makes no device. The first layer rendered does, as the first claim does,
    /// and a device that cannot be made leaves the surface rendering nothing,
    /// so the layers' painters draw what they show without a GPU.
    ReadbackSurface readback();

    /// Releases what is still claimed and destroys the device. Idempotent.
    @Override
    void close();
}
