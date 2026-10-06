package dev.goldberry.natives.sdl.gpu;

import java.lang.foreign.MemorySegment;

/// A compute pipeline: one compute shader and what it declares, bound in a
/// compute pass and dispatched over workgroups.
///
/// Read more: [The native boundary](https://goldberry.dev/docs/overview/architecture.html#the-native-boundary).
public final class SdlGpuComputePipeline extends SdlGpuResource {

    private final SdlGpuComputeCode code;

    SdlGpuComputePipeline(SdlGpuDevice device, MemorySegment handle, SdlGpuComputeCode code) {
        super(device, handle);
        this.code = code;
    }

    /// What it was made from, bytecode included.
    public SdlGpuComputeCode code() {
        return code;
    }

    /// How many textures the shader samples.
    public int samplers() {
        return code.samplers();
    }

    /// How many storage textures the shader reads.
    public int readOnlyStorageTextures() {
        return code.readOnlyStorageTextures();
    }

    /// How many storage buffers the shader reads.
    public int readOnlyStorageBuffers() {
        return code.readOnlyStorageBuffers();
    }

    /// How many storage textures the shader writes.
    public int readWriteStorageTextures() {
        return code.readWriteStorageTextures();
    }

    /// How many storage buffers the shader writes.
    public int readWriteStorageBuffers() {
        return code.readWriteStorageBuffers();
    }

    /// How many uniform blocks the shader reads.
    public int uniformBuffers() {
        return code.uniformBuffers();
    }

    @Override
    void release(MemorySegment device, MemorySegment handle) {
        device().calls().pipelines().releaseGPUComputePipeline().call(device, handle);
    }

    @Override
    public String toString() {
        return "SdlGpuComputePipeline[" + code.threadsX() + "x" + code.threadsY() + "x" + code.threadsZ()
                + " threads, reads " + code.readOnlyStorageBuffers() + " buffers, writes "
                + code.readWriteStorageBuffers() + "]";
    }
}
