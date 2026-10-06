package dev.goldberry.natives.sdl.gpu;

import java.lang.foreign.MemorySegment;

import dev.goldberry.natives.sdl.gpu.enums.SdlGpuAddressMode;
import dev.goldberry.natives.sdl.gpu.enums.SdlGpuFilter;

/// How a shader reads a texture, as its [SdlGpuSamplerDescription] says.
///
/// Read more: [The native boundary](https://goldberry.dev/docs/overview/architecture.html#the-native-boundary).
public final class SdlGpuSampler extends SdlGpuResource {

    private final SdlGpuSamplerDescription description;

    SdlGpuSampler(SdlGpuDevice device, MemorySegment handle, SdlGpuSamplerDescription description) {
        super(device, handle);
        this.description = description;
    }

    /// What it was made from.
    public SdlGpuSamplerDescription description() {
        return description;
    }

    /// What it reads outside 0 to 1, on every axis.
    public SdlGpuAddressMode addressMode() {
        return description.addressMode();
    }

    /// How it filters, minifying and magnifying alike.
    public SdlGpuFilter filter() {
        return description.filter();
    }

    /// Whether a lookup compares against a reference depth rather than
    /// returning the texel.
    public boolean compares() {
        return description.compare().isPresent();
    }

    @Override
    void release(MemorySegment device, MemorySegment handle) {
        device().calls().pipelines().releaseGPUSampler().call(device, handle);
    }

    @Override
    public String toString() {
        return "SdlGpuSampler[" + description.filter() + ", " + description.addressMode()
                + description.mipFilter().map(filter -> ", mip " + filter).orElse("")
                + description.compare().map(op -> ", compare " + op).orElse("") + "]";
    }
}
