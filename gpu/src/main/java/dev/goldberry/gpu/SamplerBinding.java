package dev.goldberry.gpu;

import java.util.Objects;

import dev.goldberry.natives.sdl.gpu.SdlGpuSamplerBinding;

/// One sampler slot of a shader: the texture it reads, and the sampler it reads
/// it with.
///
/// What [RenderPass#bindFragmentSamplers(java.util.List)] takes, one per slot,
/// so that a shader reading two textures can read each its own way: a texture
/// array trilinearly, and a shadow map through a comparison sampler.
///
/// ```java
/// pass.bindFragmentSamplers(List.of(
///         new SamplerBinding(faces, trilinear),
///         new SamplerBinding(shadowMap, shadowCompare)));
/// ```
///
/// @param texture what the slot reads; made with [TextureUsage#SAMPLER]
/// @param sampler how it reads it
public record SamplerBinding(GpuTexture texture, GpuSampler sampler) {

    /// Checks neither is null.
    public SamplerBinding {
        Objects.requireNonNull(texture, "texture");
        Objects.requireNonNull(sampler, "sampler");
    }

    /// The SDL binding, for `user`'s commands.
    SdlGpuSamplerBinding sdl(GpuDevice user) {
        return new SdlGpuSamplerBinding(texture.sdl(user), sampler.sdl(user));
    }
}
