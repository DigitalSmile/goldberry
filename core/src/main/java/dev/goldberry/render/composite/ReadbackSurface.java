package dev.goldberry.render.composite;

import dev.goldberry.render.window.GpuSurface;

/// A [GpuSurface.ReadBack] a [Compositor] made for one window, or for a
/// backend with no windows the GPU can claim: its GPU layers are rendered to
/// textures and read back into the painted frame.
///
/// It holds what rendering its layers needs -- a texture per layer, at the
/// layer's size -- from the first layer rendered until the layer is no longer
/// placed, or until it is closed. The device is the compositor's, made at the
/// first layer and not before.
///
/// Confined to the UI thread.
///
/// Read more: [Logging and diagnostics](https://goldberry.dev/docs/guide/logging.html#which-way-a-window-presents).
public interface ReadbackSurface extends GpuSurface.ReadBack, AutoCloseable {

    /// Releases what it holds. Rendering afterwards gives nothing. Idempotent.
    @Override
    void close();
}
