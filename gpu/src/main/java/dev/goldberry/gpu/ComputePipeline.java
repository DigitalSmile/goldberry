package dev.goldberry.gpu;

import dev.goldberry.natives.sdl.gpu.SdlGpuComputePipeline;

/// A compute pipeline on a [GpuDevice], made from [ComputeCode]: bound in a
/// [ComputePass] and dispatched over workgroups.
public final class ComputePipeline extends GpuResource {

    private final SdlGpuComputePipeline sdl;
    private final ComputeCode code;
    private final ShaderFormat format;

    ComputePipeline(GpuDevice device, SdlGpuComputePipeline sdl, ComputeCode code, ShaderFormat format) {
        super(device, sdl);
        this.sdl = sdl;
        this.code = code;
        this.format = format;
    }

    /// What it was made from.
    public ComputeCode code() {
        return code;
    }

    /// The format it was made in: the one of its code the device takes.
    public ShaderFormat format() {
        return format;
    }

    /// The SDL pipeline, for `user`'s passes.
    SdlGpuComputePipeline sdl(GpuDevice user) {
        requireUsableBy(user);
        return sdl;
    }

    @Override
    public String toString() {
        return "ComputePipeline[" + code.threadsX() + "x" + code.threadsY() + "x" + code.threadsZ() + " threads, "
                + format + (isClosed() ? ", closed]" : "]");
    }
}
