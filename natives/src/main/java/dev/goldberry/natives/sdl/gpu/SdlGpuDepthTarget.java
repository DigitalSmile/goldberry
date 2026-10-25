package dev.goldberry.natives.sdl.gpu;

import java.util.Objects;

import dev.goldberry.natives.sdl.gpu.enums.SdlGpuTextureUsage;

/// The depth texture a render pass tests and writes, and what the pass does with
/// it first. Its stencil, where its format has one, is cleared or kept with the
/// depth, and kept at the pass's end.
///
/// Read more: [The native boundary](https://goldberry.dev/docs/overview/architecture.html#the-native-boundary).
///
/// @param texture      a texture of a depth format, made for
///                     [SdlGpuTextureUsage#DEPTH_STENCIL_TARGET]
/// @param clear        whether the pass clears it first, rather than keeping
///                     what it holds
/// @param clearDepth   the depth it is cleared to, 0 to 1: 1 is the far plane.
///                     Ignored when not clearing
/// @param clearStencil the stencil it is cleared to, 0 to 255. Ignored when not
///                     clearing, or when the format has no stencil
public record SdlGpuDepthTarget(SdlGpuTexture texture, boolean clear, float clearDepth, int clearStencil) {

    /// Checks the texture can be a depth target and the values are in range.
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
        if (clearStencil < 0 || clearStencil > 0xFF) {
            throw new IllegalArgumentException("clear stencil " + clearStencil + " outside 0 to 255");
        }
    }

    /// A target whose stencil, if any, is cleared to 0.
    public SdlGpuDepthTarget(SdlGpuTexture texture, boolean clear, float clearDepth) {
        this(texture, clear, clearDepth, 0);
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
