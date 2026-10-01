package dev.goldberry.natives.sdl.gpu;

import java.util.Objects;

import dev.goldberry.natives.sdl.gpu.enums.SdlGpuTextureUsage;

/// The depth texture a render pass tests and writes, and what the pass does with
/// it first.
///
/// @param texture    a texture of a depth format, made for
///                   [SdlGpuTextureUsage#DEPTH_STENCIL_TARGET]
/// @param clear      whether the pass clears it first, rather than keeping what
///                   it holds
/// @param clearDepth the depth it is cleared to, 0 to 1: 1 is the far plane.
///                   Ignored when not clearing
public record SdlGpuDepthTarget(SdlGpuTexture texture, boolean clear, float clearDepth) {

    /// Checks the texture can be a depth target and the depth is in range.
    public SdlGpuDepthTarget {
        Objects.requireNonNull(texture, "texture");
        if (!texture.format().isDepth()) {
            throw new IllegalArgumentException(texture + " is not of a depth format");
        }
        if (!texture.usages().contains(SdlGpuTextureUsage.DEPTH_STENCIL_TARGET)) {
            throw new IllegalArgumentException(texture + " was not made to be a depth target");
        }
        if (!(clearDepth >= 0 && clearDepth <= 1)) {
            throw new IllegalArgumentException("clear depth " + clearDepth + " outside 0 to 1");
        }
    }

    /// Clears `texture` to `depth` first.
    public static SdlGpuDepthTarget clear(SdlGpuTexture texture, float depth) {
        return new SdlGpuDepthTarget(texture, true, depth);
    }

    /// Keeps what `texture` holds.
    public static SdlGpuDepthTarget keep(SdlGpuTexture texture) {
        return new SdlGpuDepthTarget(texture, false, 1);
    }
}
