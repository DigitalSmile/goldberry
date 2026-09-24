package io.github.digitalsmile.goldberry.render.composite;

import io.github.digitalsmile.goldberry.natives.sdl.SdlWindowHandle;

/// Presents windows through the GPU: `:gpu`'s, found by `ServiceLoader` the
/// first time a window is to be composited, and one per backend.
///
/// It owns the process's one GPU device, made on the first [#claim] and never
/// before (`docs/gpu-plan.md`, D2), so an application that composites nothing
/// never loads a driver. By default that is the first frame of the first window
/// (ADR-0480). A device that cannot be made is remembered, and every later claim
/// is refused at once, as a failed hardware decoder is (ADR-0470).
///
/// Confined to the UI thread, which on macOS is the process's first.
public interface Compositor extends AutoCloseable {

    /// Takes `window` over: from now on it presents through a swapchain, and has
    /// no window surface. The caller has given the surface up first.
    ///
    /// [Claim.Refused] when it cannot be done -- no device, or a window the
    /// driver will not claim -- with the reason. The window stays on the CPU,
    /// and nothing is left claimed.
    Claim claim(SdlWindowHandle window);

    /// Releases what is still claimed and destroys the device. Idempotent.
    @Override
    void close();
}
