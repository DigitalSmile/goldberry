package io.github.digitalsmile.goldberry.media.view;

import io.github.digitalsmile.goldberry.media.Picture;
import io.github.digitalsmile.goldberry.paint.Frame;
import io.github.digitalsmile.goldberry.widgets.core.image.Fit;

/// Shows a view's pictures through the GPU: `video-view`'s GPU present
/// (`docs/gpu-plan.md`, phase 6; ADR-0484).
///
/// Written in this module's types alone, so that a view can hold one without
/// `:gpu` on the module path. [GpuVideo] makes the one implementation, which is
/// where `:gpu`'s types are used, and only when `:gpu` is there.
///
/// One per view that shows pictures, kept while it is mounted, since the layer
/// it places has to be the same object from frame to frame. Confined to the UI
/// thread.
interface VideoPresenter extends AutoCloseable {

    /// Places `picture` as a GPU layer over `placement`'s rectangle in `frame`,
    /// cropped to its source.
    ///
    /// @return true when it was placed; false when the frame cannot show a GPU
    ///         layer (no GPU, `goldberry.gpu=off`, inside a group), and the
    ///         caller paints the picture on the CPU instead
    boolean place(Frame frame, Picture picture, Fit.Placement placement);

    /// Releases the layer and what it made on the device. Idempotent.
    @Override
    void close();
}
