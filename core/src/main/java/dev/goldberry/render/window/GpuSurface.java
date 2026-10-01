package dev.goldberry.render.window;

import java.util.List;
import java.util.Optional;

import dev.goldberry.render.GpuContent;
import dev.goldberry.render.GpuPlacement;
import dev.goldberry.render.PixelBuffer;
import dev.goldberry.render.model.PhysicalSize;

/// How a window shows GPU layers: [BackendWindow#gpuSurface()]
/// (`docs/gpu-plan.md`, D3 and D5; ADR-0481).
///
/// A frame painted over a surface hands it every GPU layer it places. Which of
/// the two kinds the surface is decides what the frame does with one:
///
/// | Kind | The layer's box in the frame | The layer's pixels |
/// |---|---|---|
/// | [Composited] | cleared to transparent: a hole | drawn under the frame at present |
/// | [ReadBack] | replaced by [ReadBack#render]'s pixels | rendered, downloaded, drawn now |
///
/// A window with no surface at all -- no GPU, or `goldberry.gpu=off` -- has
/// none to give, and a frame over it tells the painter so, which then paints
/// what it shows instead.
///
/// The two kinds are the window's composition mode: sealed, so a caller that
/// asks which one it has switches over them rather than over a second enum
/// that could disagree. Confined to the UI thread, as a window is.
public sealed interface GpuSurface permits GpuSurface.Composited, GpuSurface.ReadBack {

    /// What the frame just painted placed, in paint order, and every layer it
    /// kept from the frame before where only part of it was repainted.
    ///
    /// Called once a frame, after painting and before the window presents,
    /// with an empty list when there are none: that too is news, since a
    /// surface may hold what it made for a layer until the layer stops being
    /// placed.
    void placed(List<GpuPlacement> layers);

    /// The window presents through the GPU (ADR-0479). Its frames have holes
    /// where layers are, and [#placed] is what its next present draws in them,
    /// in paint order, under the frame.
    non-sealed interface Composited extends GpuSurface {}

    /// The window presents on the CPU, or has no window at all: headless,
    /// offscreen, a popup the GPU will not claim. Layers are rendered on the
    /// GPU, downloaded, and drawn into the frame as pixels.
    non-sealed interface ReadBack extends GpuSurface {

        /// Renders `content` at `size` and brings the pixels back: premultiplied
        /// BGRA, `size` exactly. Blocks until the GPU is done, which is the
        /// price of this mode.
        ///
        /// Empty when it cannot: no GPU device, content that is not a GPU layer,
        /// or a layer that failed. The frame then tells the painter, which paints
        /// what it shows without a GPU.
        Optional<PixelBuffer> render(GpuContent content, PhysicalSize size);
    }
}
