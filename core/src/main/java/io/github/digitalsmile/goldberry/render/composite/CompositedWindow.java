package io.github.digitalsmile.goldberry.render.composite;

import java.util.List;

import io.github.digitalsmile.goldberry.render.DamageRect;
import io.github.digitalsmile.goldberry.render.GpuPlacement;
import io.github.digitalsmile.goldberry.render.PixelBuffer;
import io.github.digitalsmile.goldberry.render.PresentTimings;

/// A window a [Compositor] has claimed: its painted frames are uploaded to the
/// GPU, damage only, and composited onto its swapchain.
///
/// Confined to the UI thread.
public interface CompositedWindow extends AutoCloseable {

    /// Uploads `damage` of `frame`, renders `layers`, and presents them: each
    /// layer where it was placed, in paint order, and the frame over them all
    /// (`docs/gpu-plan.md`, D4; ADR-0481).
    ///
    /// Waits for the swapchain when the GPU is a frame ahead, which is what paces
    /// a composited window to its display. `frame` is borrowed for the call. A
    /// minimised or occluded window has no swapchain texture: the upload still
    /// lands, nothing is shown, and the call returns.
    ///
    /// With no damage and no layers nothing has changed, and nothing is
    /// presented. With layers something may have, and the window is composited
    /// again whatever the damage.
    ///
    /// @param frame  the painted frame, premultiplied BGRA, with a hole where
    ///               each layer is
    /// @param damage what changed since the last frame, inside it; the first
    ///               frame, and the first after a resize, is whole
    /// @param layers the GPU layers on screen, in paint order
    /// @throws RuntimeException when the GPU fails: a lost device. The caller
    ///                          closes this and presents on the CPU
    void present(PixelBuffer frame, List<DamageRect> damage, List<GpuPlacement> layers);

    /// What the last [#present] cost, for a frame loop's statistics:
    /// [PresentTimings#NONE] when it presented nothing, for want of damage.
    PresentTimings lastPresent();

    /// Gives the window back: its surface can be asked for again. Idempotent.
    @Override
    void close();
}
