package io.github.digitalsmile.goldberry.gpu.view;

import java.util.EnumSet;
import java.util.Objects;

import org.jspecify.annotations.Nullable;

import io.github.digitalsmile.goldberry.gpu.GpuDevice;
import io.github.digitalsmile.goldberry.gpu.GpuFrame;
import io.github.digitalsmile.goldberry.gpu.GpuLayer;
import io.github.digitalsmile.goldberry.gpu.GpuTexture;
import io.github.digitalsmile.goldberry.gpu.TextureFormat;
import io.github.digitalsmile.goldberry.gpu.TextureSpec;
import io.github.digitalsmile.goldberry.gpu.TextureUsage;
import io.github.digitalsmile.goldberry.render.model.PhysicalSize;

/// A `canvas3d`'s GPU layer: its [Canvas3dRenderer], driven through the
/// renderer's lifecycle, and the depth texture it draws with (ADR-0482).
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

    /// Bumped by [#redraw]; the picture is current when [#drawn] has caught up.
    private long wanted = 1;
    private long drawn;

    /// The device the renderer was initialised on, or null before that.
    private @Nullable GpuDevice device;
    private @Nullable PhysicalSize size;
    private @Nullable GpuTexture depth;
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
        var now = new PhysicalSize(target.width(), target.height());
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
        renderer.render(frame, new Canvas3dTarget(target, depth, nanos));
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

    /// Back to before the first frame: the renderer disposed and the depth
    /// texture released, on a device that is still open.
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
        renderer.dispose();
    }

    @Override
    public String toString() {
        return "canvas3d[" + renderer.getClass().getSimpleName() + "]";
    }
}
