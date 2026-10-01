package dev.goldberry.gpu;

import java.util.Objects;

import dev.goldberry.paint.Painter;
import dev.goldberry.render.GpuContent;

/// Pixels the GPU draws into a window, in the tree among what the CPU paints:
/// what `canvas3d` and a video on the GPU are (`docs/gpu-plan.md`, D4 and D5;
/// ADR-0481).
///
/// A layer is placed by a painter, like anything else in a frame, and drawn by
/// the toolkit:
///
/// ```java
/// Box.of().size(Length.points(320), Length.points(180))
///         .painting(layer.painter((frame, size) -> frame.fillRect(0, 0, size.width(), size.height(), GREY)));
/// ```
///
/// What is painted after it is above it, and what was painted before it under
/// its box is hidden. Where the window presents through the GPU the layer is
/// drawn into the window's composite; elsewhere it is rendered, read back and
/// drawn into the frame as pixels. [#render] is asked the same thing either way.
///
/// **The rules** (ADR-0481):
///
/// - **Opaque.** A layer hides what is under it, as an opaque box does. What it
///   leaves translucent does not show the frame through.
/// - **A rectangle of whole pixels,** axis-aligned. Clips cut it to their
///   bounding rectangle, so a rounded corner over a layer is drawn by the UI
///   painted over it.
/// - **Not in a group.** A layer inside an `opacity` group or a promoted layer
///   cannot be shown, and its painter's fallback is drawn there instead.
///
/// Compared by identity: the same object placed on every frame is one layer,
/// and the texture it renders into is kept for it between frames.
public interface GpuLayer extends GpuContent {

    /// Renders this layer's picture into `target`, a texture exactly the size
    /// the layer covers in physical pixels, recording into `frame`.
    ///
    /// Called on the UI thread, once for each frame the layer is shown on. The
    /// layer covers every pixel of `target`: its first render pass clears it,
    /// usually. The frame is submitted by the caller, and the layer does not
    /// wait for the GPU.
    ///
    /// Resources the layer makes -- pipelines, buffers -- are made on
    /// `frame.device()`, are the layer's own, and live until it closes them.
    /// `target` is the toolkit's, and is only for this call.
    ///
    /// A layer that throws is shown as nothing -- black -- on the frames it
    /// throws on, and the error is logged once.
    ///
    /// @param frame  the frame to record into, on the window's device
    /// @param target a colour target of [TextureFormat#B8G8R8A8_UNORM] at the
    ///               layer's size, sampled afterwards by the compositor
    void render(GpuFrame frame, GpuTexture target);

    /// Whether this layer's picture has changed since it was last rendered.
    ///
    /// Asked on each frame the layer is shown on, before [#render]. False lets
    /// the toolkit show what the layer rendered last, where it still holds it:
    /// a 3D view that is not moving is not drawn again for a caret blinking
    /// beside it. The toolkit renders anyway when it holds nothing -- the first
    /// frame, a new size, a layer scrolled back into view -- so false is never
    /// wrong, only a promise that the last picture is still right.
    ///
    /// True by default: a layer that says nothing is rendered on every frame.
    default boolean needsRender() {
        return true;
    }

    /// A painter that places this layer in the box it paints, whole, and runs
    /// `fallback` there instead when the frame cannot show it: no GPU,
    /// `goldberry.gpu=off`, or a layer inside a group.
    ///
    /// @param fallback what to paint without a GPU, as a `canvas` would
    default Painter painter(Painter fallback) {
        Objects.requireNonNull(fallback, "fallback");
        return (frame, size) -> {
            if (!frame.gpuLayer(this, 0, 0, size.width(), size.height())) {
                fallback.paint(frame, size);
            }
        };
    }
}
