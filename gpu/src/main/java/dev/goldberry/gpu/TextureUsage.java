package dev.goldberry.gpu;

import java.util.EnumSet;
import java.util.Set;

import dev.goldberry.natives.sdl.gpu.enums.SdlGpuTextureUsage;

/// What a texture may be used for. A texture is made for a set of them, and a
/// use it was not made for is refused before the GPU sees it.
public enum TextureUsage {
    /// Read by a shader through a sampler.
    SAMPLER,
    /// Rendered into, cleared, or blitted into.
    COLOR_TARGET,
    /// Tested and written as a render pass's depth; sampled too when made with
    /// [#SAMPLER], as a shadow map is.
    DEPTH_TARGET,
    /// Read as a storage texture, texel by texel, by a vertex or fragment
    /// shader.
    GRAPHICS_STORAGE_READ,
    /// Read as a storage texture by a compute shader.
    COMPUTE_STORAGE_READ,
    /// Written as a storage texture by a compute shader.
    COMPUTE_STORAGE_WRITE;

    SdlGpuTextureUsage sdl() {
        return switch (this) {
            case SAMPLER -> SdlGpuTextureUsage.SAMPLER;
            case COLOR_TARGET -> SdlGpuTextureUsage.COLOR_TARGET;
            case DEPTH_TARGET -> SdlGpuTextureUsage.DEPTH_STENCIL_TARGET;
            case GRAPHICS_STORAGE_READ -> SdlGpuTextureUsage.GRAPHICS_STORAGE_READ;
            case COMPUTE_STORAGE_READ -> SdlGpuTextureUsage.COMPUTE_STORAGE_READ;
            case COMPUTE_STORAGE_WRITE -> SdlGpuTextureUsage.COMPUTE_STORAGE_WRITE;
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
