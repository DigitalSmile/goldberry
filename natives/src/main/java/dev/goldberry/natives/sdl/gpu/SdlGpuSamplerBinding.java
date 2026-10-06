package dev.goldberry.natives.sdl.gpu;

import java.util.Objects;

/// One sampler slot of a shader: the texture it reads and the sampler it reads
/// it with, as SDL's `SDL_GPUTextureSamplerBinding`.
///
/// A slot each, so one shader can read a texture trilinearly and a depth map
/// through a comparison sampler.
///
/// Read more: [The native boundary](https://goldberry.dev/docs/overview/architecture.html#the-native-boundary).
///
/// @param texture what the slot reads
/// @param sampler how it reads it
public record SdlGpuSamplerBinding(SdlGpuTexture texture, SdlGpuSampler sampler) {

    /// Checks neither is null.
    public SdlGpuSamplerBinding {
        Objects.requireNonNull(texture, "texture");
        Objects.requireNonNull(sampler, "sampler");
    }
}
