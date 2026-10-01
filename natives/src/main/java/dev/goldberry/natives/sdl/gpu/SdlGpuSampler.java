package dev.goldberry.natives.sdl.gpu;

import java.lang.foreign.MemorySegment;

import dev.goldberry.natives.sdl.gpu.enums.SdlGpuAddressMode;
import dev.goldberry.natives.sdl.gpu.enums.SdlGpuFilter;

/// How a shader reads a texture: filtered one way, addressed one way outside 0
/// to 1, one mip level.
public final class SdlGpuSampler extends SdlGpuResource {

    private final SdlGpuFilter filter;
    private final SdlGpuAddressMode addressMode;

    SdlGpuSampler(SdlGpuDevice device, MemorySegment handle, SdlGpuFilter filter, SdlGpuAddressMode addressMode) {
        super(device, handle);
        this.filter = filter;
        this.addressMode = addressMode;
    }

    /// What it reads outside 0 to 1, on both axes.
    public SdlGpuAddressMode addressMode() {
        return addressMode;
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
        return "SdlGpuSampler[" + filter + ", " + addressMode + "]";
    }
}
