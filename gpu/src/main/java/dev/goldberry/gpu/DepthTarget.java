package dev.goldberry.gpu;

import java.util.Objects;

/// The depth texture a render pass tests and writes, and what the pass does with
/// it first. It must be the size of the pass's colour target. Where its format
/// has a stencil, the stencil is cleared or kept with the depth.
///
/// @param texture      a texture made by [TextureSpec#depth]
/// @param clear        whether the pass clears it first, rather than keeping
///                     what it holds
/// @param clearDepth   the depth it is cleared to, 0 to 1; ignored when keeping
/// @param clearStencil the stencil it is cleared to, 0 to 255; ignored when
///                     keeping, or when the format has no stencil
public record DepthTarget(GpuTexture texture, boolean clear, float clearDepth, int clearStencil) {

    /// A target whose stencil, if it has one, is cleared to 0.
    public DepthTarget(GpuTexture texture, boolean clear, float clearDepth) {
        this(texture, clear, clearDepth, 0);
    }

    /// Checks the texture can be a depth target and the values are in range.
    public DepthTarget {
        Objects.requireNonNull(texture, "texture");
        if (!texture.usages().contains(TextureUsage.DEPTH_TARGET)) {
            throw new IllegalArgumentException(texture + " was not made to be a depth target");
        }
        if (!(clearDepth >= 0 && clearDepth <= 1)) {
            throw new IllegalArgumentException("clear depth " + clearDepth + " outside 0 to 1");
        }
        if (clearStencil < 0 || clearStencil > StencilTest.ALL_BITS) {
            throw new IllegalArgumentException("clear stencil " + clearStencil + " outside 0 to 255");
        }
    }

    /// Clears `texture` to the far plane, 1, first: what [DepthTest#less] wants.
    public static DepthTarget clear(GpuTexture texture) {
        return new DepthTarget(texture, true, 1);
    }

    /// Clears `texture` to `depth` first.
    public static DepthTarget clear(GpuTexture texture, float depth) {
        return new DepthTarget(texture, true, depth);
    }

    /// Clears `texture` to `depth` and its stencil to `stencil` first.
    ///
    /// @throws IllegalArgumentException when its format has no stencil
    public static DepthTarget clear(GpuTexture texture, float depth, int stencil) {
        if (!texture.format().hasStencil()) {
            throw new IllegalArgumentException(texture + " has no stencil to clear to " + stencil);
        }
        return new DepthTarget(texture, true, depth, stencil);
    }

    /// Keeps what `texture` holds: a second pass over the same scene.
    public static DepthTarget keep(GpuTexture texture) {
        return new DepthTarget(texture, false, 1);
    }
}
