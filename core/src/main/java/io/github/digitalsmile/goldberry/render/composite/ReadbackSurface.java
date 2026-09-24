package io.github.digitalsmile.goldberry.render.composite;

import io.github.digitalsmile.goldberry.render.window.GpuSurface;

/// A [GpuSurface.ReadBack] a [Compositor] made for one window, or for a
/// backend with no windows the GPU can claim (`docs/gpu-plan.md`, D3; ADR-0481).
///
/// It holds what rendering its layers needs -- a texture per layer, at the
/// layer's size -- from the first layer rendered until the layer is no longer
/// placed, or until it is closed. The device is the compositor's, made at the
/// first layer and not before.
///
/// Confined to the UI thread.
public interface ReadbackSurface extends GpuSurface.ReadBack, AutoCloseable {

    /// Releases what it holds. Rendering afterwards gives nothing. Idempotent.
    @Override
    void close();
}
