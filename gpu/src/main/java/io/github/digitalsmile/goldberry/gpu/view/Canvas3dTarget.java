package io.github.digitalsmile.goldberry.gpu.view;

import java.util.Objects;
import java.util.Optional;

import org.jspecify.annotations.Nullable;

import io.github.digitalsmile.goldberry.gpu.DepthTarget;
import io.github.digitalsmile.goldberry.gpu.GpuTexture;
import io.github.digitalsmile.goldberry.render.model.PhysicalSize;

/// What a [Canvas3dRenderer] draws one frame into (ADR-0482).
///
/// @param colour a colour target at the canvas's size in physical pixels, in
///               [io.github.digitalsmile.goldberry.gpu.TextureFormat#B8G8R8A8_UNORM];
///               the toolkit's, for this frame only
/// @param depth  a depth target of the same size, when the canvas asked for
///               one, and null otherwise; the canvas's, kept between frames
/// @param nanos  the frame's time on the window's clock, in nanoseconds since
///               the canvas was first drawn: what an animation is a function
///               of, so a virtual clock makes a frame exact
public record Canvas3dTarget(GpuTexture colour, @Nullable GpuTexture depth, long nanos) {

    /// Checks the two textures agree.
    ///
    /// @throws IllegalArgumentException when the depth texture is another size
    public Canvas3dTarget {
        Objects.requireNonNull(colour, "colour");
        if (depth != null && (depth.width() != colour.width() || depth.height() != colour.height())) {
            throw new IllegalArgumentException("a depth target " + depth.width() + "x" + depth.height()
                    + " for a colour target " + colour.width() + "x" + colour.height());
        }
        if (nanos < 0) {
            throw new IllegalArgumentException("a frame at " + nanos + " ns");
        }
    }

    /// The canvas's size in physical pixels.
    public PhysicalSize size() {
        return new PhysicalSize(colour.width(), colour.height());
    }

    /// The width over the height: what a perspective projection is made with.
    public double aspect() {
        return (double) colour.width() / colour.height();
    }

    /// The depth texture, when the canvas has one.
    public Optional<GpuTexture> depthTexture() {
        return Optional.ofNullable(depth);
    }

    /// The depth target cleared to the far plane, for a render pass that draws
    /// with depth.
    ///
    /// @throws IllegalStateException when the canvas asked for no depth
    public DepthTarget clearDepth() {
        var texture = depth;
        if (texture == null) {
            throw new IllegalStateException("this canvas has no depth target: give it one with depth=");
        }
        return DepthTarget.clear(texture);
    }

    /// The frame's time in seconds, as a double.
    public double seconds() {
        return nanos / 1e9;
    }
}
