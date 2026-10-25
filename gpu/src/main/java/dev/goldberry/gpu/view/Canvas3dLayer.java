package dev.goldberry.gpu.view;

import java.util.EnumSet;
import java.util.Objects;

import org.jspecify.annotations.Nullable;

import dev.goldberry.gpu.GpuDevice;
import dev.goldberry.gpu.GpuFrame;
import dev.goldberry.gpu.GpuLayer;
import dev.goldberry.gpu.GpuTexture;
import dev.goldberry.gpu.TextureFormat;
import dev.goldberry.gpu.TextureSpec;
import dev.goldberry.gpu.TextureUsage;
import dev.goldberry.gpu.upscale.RenderScale;
import dev.goldberry.gpu.upscale.Upscaler;
import dev.goldberry.render.model.PhysicalSize;

/// A `canvas3d`'s GPU layer: its [Canvas3dRenderer], driven through the
/// renderer's lifecycle, and the depth texture it draws with.
///
/// At a [RenderScale] other than native the renderer draws into a colour
/// texture of the layer's own at the scaled size, which an [Upscaler] then
/// brings to the target's.
///
/// One per mounted canvas and renderer, kept between frames, so the compositor
/// keeps its texture: a canvas that is not redrawn is shown from it
/// ([GpuLayer#needsRender]).
///
/// Confined to the UI thread.
final class Canvas3dLayer implements GpuLayer, AutoCloseable {

    private final Canvas3dRenderer renderer;
    private final @Nullable TextureFormat depthFormat;

    private boolean continuous;
    private long nanos;
    private RenderScale scale = RenderScale.NATIVE;

    /// Bumped by [#redraw]; the picture is current when [#drawn] has caught up.
    private long wanted = 1;
    private long drawn;

    /// The device the renderer was initialised on, or null before that.
    private @Nullable GpuDevice device;
    /// The size the renderer was last told it draws at.
    private @Nullable PhysicalSize size;

    private @Nullable GpuTexture depth;

    /// What the renderer draws into when it is not drawn at the target's size.
    private @Nullable GpuTexture scaled;

    private @Nullable Upscaler upscaler;
    private boolean closed;

    /// @param depthFormat a depth format for the renderer's depth target, or
    ///                    null for none
    Canvas3dLayer(Canvas3dRenderer renderer, @Nullable TextureFormat depthFormat) {
        this.renderer = Objects.requireNonNull(renderer, "renderer");
        if (depthFormat != null && !depthFormat.isDepth()) {
            throw new IllegalArgumentException(depthFormat + " is not a depth format");
        }
        this.depthFormat = depthFormat;
    }

    /// What the frame being painted says: its time on the window's clock, and
    /// whether the canvas draws every frame.
    void frame(long nanos, boolean continuous) {
        this.nanos = Math.max(0, nanos);
        this.continuous = continuous;
    }

    /// The scale the frame being painted draws at: a new one has the picture
    /// drawn again.
    void scale(RenderScale value) {
        Objects.requireNonNull(value, "value");
        if (!value.equals(scale)) {
            scale = value;
            wanted++;
        }
    }

    /// The scale it draws at.
    RenderScale scale() {
        return scale;
    }

    /// Asks for the picture to be drawn again on the next frame it is shown on.
    void redraw() {
        wanted++;
    }

    @Override
    public boolean needsRender() {
        return !closed && (continuous || drawn != wanted);
    }

    @Override
    public void render(GpuFrame frame, GpuTexture target) {
        if (closed) {
            throw new IllegalStateException("this canvas has left the tree");
        }
        var current = frame.device();
        if (device != current) {
            // A first frame, or a new device: whatever the renderer made on the
            // old one went with it.
            release();
            renderer.init(current);
            device = current;
        }
        var shown = new PhysicalSize(target.width(), target.height());
        var now = scale.drawSize(shown);
        if (!now.equals(size)) {
            if (depth != null) {
                depth.close();
                depth = null;
            }
            if (depthFormat != null) {
                depth = current.createTexture(
                        new TextureSpec(depthFormat, now.width(), now.height(), EnumSet.of(TextureUsage.DEPTH_TARGET)));
            }
            renderer.resize(now);
            size = now;
        }
        drawn = wanted;
        if (now.equals(shown)) {
            closeScaled();
            renderer.render(frame, new Canvas3dTarget(target, depth, nanos));
            return;
        }
        var colour = scaled(current, target.format(), now);
        renderer.render(frame, new Canvas3dTarget(colour, depth, nanos));
        upscaler(current).upscale(frame, colour, target, scale.filter(), scale.sharpness());
    }

    /// The colour texture drawn into at `drawn`, kept while the size stays.
    private GpuTexture scaled(GpuDevice on, TextureFormat format, PhysicalSize drawn) {
        var current = scaled;
        if (current != null && current.width() == drawn.width() && current.height() == drawn.height()) {
            return current;
        }
        closeScaled();
        current = on.createTexture(TextureSpec.renderTarget(format, drawn.width(), drawn.height()));
        scaled = current;
        return current;
    }

    private Upscaler upscaler(GpuDevice on) {
        var current = upscaler;
        if (current == null) {
            current = new Upscaler(on);
            upscaler = current;
        }
        return current;
    }

    private void closeScaled() {
        var texture = scaled;
        scaled = null;
        if (texture != null && !texture.device().isClosed()) {
            texture.close();
        }
    }

    /// Disposes the renderer, if it was initialised, and releases the depth
    /// texture. Rendering afterwards throws. Idempotent.
    @Override
    public void close() {
        if (closed) {
            return;
        }
        closed = true;
        release();
    }

    /// Back to before the first frame: the renderer disposed, and the depth
    /// texture, the scaled one and the upscaler released, on a device that is
    /// still open.
    private void release() {
        var made = device;
        device = null;
        size = null;
        var texture = depth;
        depth = null;
        if (made == null) {
            return;
        }
        if (texture != null && !made.isClosed()) {
            texture.close();
        }
        closeScaled();
        var passes = upscaler;
        upscaler = null;
        if (passes != null) {
            passes.close();
        }
        renderer.dispose();
    }

    @Override
    public String toString() {
        return "canvas3d[" + renderer.getClass().getSimpleName() + "]";
    }
}
