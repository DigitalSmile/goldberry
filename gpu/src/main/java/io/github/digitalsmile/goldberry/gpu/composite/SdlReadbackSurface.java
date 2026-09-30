package io.github.digitalsmile.goldberry.gpu.composite;

import java.util.List;
import java.util.Optional;

import org.slf4j.Logger;

import io.github.digitalsmile.goldberry.gpu.GpuLayer;
import io.github.digitalsmile.goldberry.log.Logs;
import io.github.digitalsmile.goldberry.render.GpuContent;
import io.github.digitalsmile.goldberry.render.GpuPlacement;
import io.github.digitalsmile.goldberry.render.PixelBuffer;
import io.github.digitalsmile.goldberry.render.composite.ReadbackSurface;
import io.github.digitalsmile.goldberry.render.model.PhysicalSize;

/// GPU layers for a window that presents on the CPU: each rendered into its
/// texture, downloaded, and handed to the frame as pixels (`docs/gpu-plan.md`,
/// D3; ADR-0481).
///
/// The layer renders exactly as it would into a composited window, into the
/// same kind of texture, so the two modes show the same pixels. What this mode
/// costs is the wait: each layer's download blocks the UI thread until the GPU
/// has drawn it. It is the mode for what cannot be composited -- headless,
/// offscreen, a popup -- rather than the one to choose.
///
/// The device is the compositor's, made by the first layer rendered. Without one
/// nothing renders, and the frame tells the layer's painter to paint its
/// fallback. Confined to the UI thread.
final class SdlReadbackSurface implements ReadbackSurface {

    private static final Logger LOG = Logs.of(SdlReadbackSurface.class);

    private final SdlCompositor compositor;
    private final LayerTextures textures = new LayerTextures();
    private boolean closed;

    SdlReadbackSurface(SdlCompositor compositor) {
        this.compositor = compositor;
    }

    @Override
    public Optional<PixelBuffer> render(GpuContent content, PhysicalSize size) {
        if (closed || !(content instanceof GpuLayer layer) || size.isEmpty()) {
            return Optional.empty();
        }
        var device = compositor.api();
        if (device.isEmpty()) {
            return Optional.empty();
        }
        try (var frame = device.get().beginFrame()) {
            var texture = textures.render(frame, layer, size);
            if (texture == null) {
                return Optional.empty();
            }
            try (var readback = frame.readback(texture)) {
                frame.submit();
                return Optional.of(readback.awaitPixels());
            }
        } catch (RuntimeException e) {
            // The device, not the layer, which `textures` catches: a lost
            // device, or memory the driver would not give.
            LOG.warn("reading a GPU layer back failed; it shows its fallback", e);
            return Optional.empty();
        }
    }

    @Override
    public void placed(List<GpuPlacement> layers) {
        if (!closed) {
            textures.retain(layers);
        }
    }

    /// The textures held, for the tests.
    LayerTextures textures() {
        return textures;
    }

    @Override
    public void close() {
        if (closed) {
            return;
        }
        closed = true;
        textures.close();
    }
}
