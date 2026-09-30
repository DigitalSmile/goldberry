package io.github.digitalsmile.goldberry.gpu;

import java.util.EnumSet;
import java.util.Set;

import io.github.digitalsmile.goldberry.natives.sdl.gpu.enums.SdlGpuTextureUsage;

/// What a texture may be used for. A texture is made for a set of them, and a
/// use it was not made for is refused before the GPU sees it.
public enum TextureUsage {
    /// Read by a shader through a sampler.
    SAMPLER,
    /// Rendered into, cleared, or blitted into.
    COLOR_TARGET,
    /// Tested and written as a render pass's depth.
    DEPTH_TARGET;

    SdlGpuTextureUsage sdl() {
        return switch (this) {
            case SAMPLER -> SdlGpuTextureUsage.SAMPLER;
            case COLOR_TARGET -> SdlGpuTextureUsage.COLOR_TARGET;
            case DEPTH_TARGET -> SdlGpuTextureUsage.DEPTH_STENCIL_TARGET;
        };
    }

    static Set<SdlGpuTextureUsage> sdl(Set<TextureUsage> usages) {
        var mapped = EnumSet.noneOf(SdlGpuTextureUsage.class);
        for (var usage : usages) {
            mapped.add(usage.sdl());
        }
        return mapped;
    }
}
