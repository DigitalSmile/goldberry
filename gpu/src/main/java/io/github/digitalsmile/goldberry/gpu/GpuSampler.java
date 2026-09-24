package io.github.digitalsmile.goldberry.gpu;

import io.github.digitalsmile.goldberry.natives.sdl.gpu.SdlGpuSampler;

/// How a shader reads the textures bound with it, made to a [SamplerSpec].
public final class GpuSampler extends GpuResource {

    private final SdlGpuSampler sdl;
    private final SamplerSpec spec;

    GpuSampler(GpuDevice device, SdlGpuSampler sdl, SamplerSpec spec) {
        super(device, sdl);
        this.sdl = sdl;
        this.spec = spec;
    }

    /// What it was made as.
    public SamplerSpec spec() {
        return spec;
    }

    /// The SDL sampler, for `user`'s commands.
    SdlGpuSampler sdl(GpuDevice user) {
        requireUsableBy(user);
        return sdl;
    }

    @Override
    public String toString() {
        return "GpuSampler[" + spec.filter() + ", " + spec.addressMode() + (isClosed() ? ", closed]" : "]");
    }
}
