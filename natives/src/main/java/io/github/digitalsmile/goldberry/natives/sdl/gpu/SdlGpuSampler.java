package io.github.digitalsmile.goldberry.natives.sdl.gpu;

import java.lang.foreign.MemorySegment;

/// How a shader reads a texture: filtered one way, clamped to the edge, one mip
/// level.
public final class SdlGpuSampler extends SdlGpuResource {

    private final SdlGpuFilter filter;

    SdlGpuSampler(SdlGpuDevice device, MemorySegment handle, SdlGpuFilter filter) {
        super(device, handle);
        this.filter = filter;
    }

    /// How it filters, minifying and magnifying alike.
    public SdlGpuFilter filter() {
        return filter;
    }

    @Override
    void release(MemorySegment device, MemorySegment handle) {
        device().calls().pipelines().releaseGPUSampler().call(device, handle);
    }

    @Override
    public String toString() {
        return "SdlGpuSampler[" + filter + "]";
    }
}
