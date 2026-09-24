package io.github.digitalsmile.goldberry.gpu;

import java.util.Objects;

/// The depth texture a render pass tests and writes, and what the pass does with
/// it first. It must be the size of the pass's colour target.
///
/// @param texture    a texture made by [TextureSpec#depth]
/// @param clear      whether the pass clears it first, rather than keeping what
///                   it holds
/// @param clearDepth the depth it is cleared to, 0 to 1; ignored when keeping
public record DepthTarget(GpuTexture texture, boolean clear, float clearDepth) {

    /// Checks the texture can be a depth target and the depth is in range.
    public DepthTarget {
        Objects.requireNonNull(texture, "texture");
        if (!texture.usages().contains(TextureUsage.DEPTH_TARGET)) {
            throw new IllegalArgumentException(texture + " was not made to be a depth target");
        }
        if (!(clearDepth >= 0 && clearDepth <= 1)) {
            throw new IllegalArgumentException("clear depth " + clearDepth + " outside 0 to 1");
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

    /// Keeps what `texture` holds: a second pass over the same scene.
    public static DepthTarget keep(GpuTexture texture) {
        return new DepthTarget(texture, false, 1);
    }
}
