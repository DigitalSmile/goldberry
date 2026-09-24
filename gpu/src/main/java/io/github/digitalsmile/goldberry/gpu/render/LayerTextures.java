package io.github.digitalsmile.goldberry.gpu.render;

import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Set;

import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;

import io.github.digitalsmile.goldberry.gpu.GpuDevice;
import io.github.digitalsmile.goldberry.gpu.GpuFrame;
import io.github.digitalsmile.goldberry.gpu.GpuLayer;
import io.github.digitalsmile.goldberry.gpu.GpuTexture;
import io.github.digitalsmile.goldberry.gpu.TextureFormat;
import io.github.digitalsmile.goldberry.gpu.TextureSpec;
import io.github.digitalsmile.goldberry.log.Logs;
import io.github.digitalsmile.goldberry.render.GpuContent;
import io.github.digitalsmile.goldberry.render.GpuPlacement;
import io.github.digitalsmile.goldberry.render.model.PhysicalSize;

/// The textures one surface's GPU layers render into, one per layer at the
/// layer's size (ADR-0481).
///
/// A layer renders into a texture of its own rather than into the window's
/// swapchain, so that it can have passes of its own -- a depth buffer, several
/// targets -- and so that the same render serves a composited window, where the
/// texture is drawn under the frame, and a read-back one, where it is
/// downloaded. The texture is kept while the layer is placed and at the same
/// size, and released when it is not placed any more.
///
/// A layer that throws is logged once and shown as nothing on the frames it
/// throws on; the window goes on.
///
/// Confined to the UI thread.
final class LayerTextures implements AutoCloseable {

    private static final Logger LOG = Logs.of(LayerTextures.class);

    /// What every layer renders into: what the UI is, so a read-back layer comes
    /// back in the frame's own format.
    static final TextureFormat FORMAT = TextureFormat.B8G8R8A8_UNORM;

    private final IdentityHashMap<GpuContent, GpuTexture> textures = new IdentityHashMap<>();

    /// The layers that have thrown, logged once each.
    private final Set<GpuContent> failed = Collections.newSetFromMap(new IdentityHashMap<>());

    private boolean closed;

    /// Renders `layer` into its texture at `size`, recording into `frame`: the
    /// texture, or null when the layer threw.
    @Nullable
    GpuTexture render(GpuFrame frame, GpuLayer layer, PhysicalSize size) {
        if (closed) {
            throw new IllegalStateException("the layer textures are closed");
        }
        var texture = textureFor(frame.device(), layer, size);
        try {
            frame.debugGroup(layer.getClass().getSimpleName(), () -> layer.render(frame, texture));
            return texture;
        } catch (RuntimeException e) {
            if (failed.add(layer)) {
                LOG.warn("the GPU layer {} failed to render, and shows nothing where it fails", layer, e);
            }
            return null;
        }
    }

    /// Renders every layer in `layers` that is a [GpuLayer] into its texture,
    /// in one frame on `device` submitted now, and says where each is drawn:
    /// what a composited present draws under its frame. A layer placed twice is
    /// rendered once, and drawn only where it was placed at the first size.
    /// Content that is not a layer, or a layer that throws, is left out, and its
    /// hole shows black. The textures of layers no longer placed are released.
    ///
    /// @throws io.github.digitalsmile.goldberry.gpu.GpuException when the
    ///         driver refuses the frame: a lost device
    List<UiComposite.Layer> renderAll(GpuDevice device, List<GpuPlacement> layers) {
        if (layers.isEmpty()) {
            retain(layers);
            return List.of();
        }
        var drawn = new ArrayList<UiComposite.Layer>(layers.size());
        var rendered = new IdentityHashMap<GpuContent, GpuTexture>();
        try (var frame = device.beginFrame()) {
            for (var placement : layers) {
                if (!(placement.content() instanceof GpuLayer layer)) {
                    continue;
                }
                var size = placement.target().size();
                var texture = rendered.get(layer);
                if (texture == null) {
                    texture = render(frame, layer, size);
                    if (texture == null) {
                        continue;
                    }
                    rendered.put(layer, texture);
                } else if (texture.width() != size.width() || texture.height() != size.height()) {
                    continue;
                }
                drawn.add(new UiComposite.Layer(ApiAccess.texture(texture), placement.target(), placement.scissor()));
            }
            frame.submit();
        }
        retain(layers);
        return drawn;
    }

    /// Releases the textures of layers not in `placed`.
    void retain(List<GpuPlacement> placed) {
        if (textures.isEmpty()) {
            return;
        }
        Set<GpuContent> shown = Collections.newSetFromMap(new IdentityHashMap<>());
        for (var placement : placed) {
            shown.add(placement.content());
        }
        var entries = textures.entrySet().iterator();
        while (entries.hasNext()) {
            var entry = entries.next();
            if (!shown.contains(entry.getKey())) {
                entry.getValue().close();
                entries.remove();
            }
        }
        failed.retainAll(shown);
    }

    /// How many textures are held, for the tests.
    int size() {
        return textures.size();
    }

    private GpuTexture textureFor(GpuDevice device, GpuContent layer, PhysicalSize size) {
        var texture = textures.get(layer);
        if (texture != null
                && !texture.isClosed()
                && texture.device() == device
                && texture.width() == size.width()
                && texture.height() == size.height()) {
            return texture;
        }
        if (texture != null) {
            texture.close();
        }
        var made = device.createTexture(TextureSpec.renderTarget(FORMAT, size.width(), size.height()));
        textures.put(layer, made);
        return made;
    }

    /// Releases every texture. Idempotent; safe after the device is gone, which
    /// released them itself.
    @Override
    public void close() {
        if (closed) {
            return;
        }
        closed = true;
        for (var texture : textures.values()) {
            if (!texture.device().isClosed()) {
                texture.close();
            }
        }
        textures.clear();
        failed.clear();
    }
}
