package dev.goldberry.gpu;

import java.nio.ByteBuffer;

import dev.goldberry.natives.sdl.gpu.SdlGpuBuffer;
import dev.goldberry.natives.sdl.gpu.SdlGpuCommandBuffer;
import dev.goldberry.natives.sdl.gpu.SdlGpuTexture;

/// A compute pass of a [GpuFrame]: a pipeline, the storage it reads, the
/// uniforms pushed, and dispatches over the storage the pass was begun to
/// write. Open while [GpuFrame#computePass]'s body runs.
///
/// What the pass writes is named when it begins, so the driver can order the
/// writes against the draws and copies around them; what it reads is bound
/// here, after the pipeline. A buffer written by compute and read by a draw
/// is made with both usages.
public final class ComputePass {

    private final GpuDevice device;
    private final SdlGpuCommandBuffer.ComputePass sdl;
    private boolean ended;

    ComputePass(GpuDevice device, SdlGpuCommandBuffer.ComputePass sdl) {
        this.device = device;
        this.sdl = sdl;
    }

    /// Binds the pipeline the next dispatches run.
    ///
    /// @throws IllegalArgumentException when it writes another number of
    ///                                  buffers or textures than the pass was
    ///                                  begun with, or is another device's or
    ///                                  closed
    public void bindPipeline(ComputePipeline pipeline) {
        requireOpen();
        sdl.bindPipeline(pipeline.sdl(device));
    }

    /// Binds `buffers` to the shader's read-only storage slots from 0: every
    /// slot its code declares, and no more.
    ///
    /// @throws IllegalStateException    when no pipeline is bound
    /// @throws IllegalArgumentException when the count is not the shader's, a
    ///                                  buffer was not made with
    ///                                  [BufferUsage#COMPUTE_STORAGE_READ], or
    ///                                  anything is another device's or closed
    public void bindStorageBuffers(GpuBuffer... buffers) {
        requireOpen();
        var sdlBuffers = new SdlGpuBuffer[buffers.length];
        for (var i = 0; i < buffers.length; i++) {
            sdlBuffers[i] = buffers[i].sdl(device);
        }
        sdl.bindStorageBuffers(sdlBuffers);
    }

    /// Binds `textures` to the shader's read-only storage slots from 0: every
    /// slot its code declares, and no more.
    ///
    /// @throws IllegalStateException    when no pipeline is bound
    /// @throws IllegalArgumentException when the count is not the shader's, a
    ///                                  texture was not made with
    ///                                  [TextureUsage#COMPUTE_STORAGE_READ], or
    ///                                  anything is another device's or closed
    public void bindStorageTextures(GpuTexture... textures) {
        requireOpen();
        var sdlTextures = new SdlGpuTexture[textures.length];
        for (var i = 0; i < textures.length; i++) {
            sdlTextures[i] = textures[i].sdl(device);
        }
        sdl.bindStorageTextures(sdlTextures);
    }

    /// Sets the shader's uniform block `slot` to `values`, for the dispatches
    /// that follow; HLSL packs a `cbuffer` in 16-byte rows.
    ///
    /// @throws IllegalArgumentException when the bound pipeline reads no such
    ///                                  block, or there are no values
    public void pushUniforms(int slot, float... values) {
        requireOpen();
        sdl.pushUniforms(slot, values);
    }

    /// Sets the shader's uniform block `slot` to the remaining bytes of
    /// `data`, which is not moved, for the dispatches that follow.
    ///
    /// @throws IllegalArgumentException as [#pushUniforms(int, float...)]
    public void pushUniforms(int slot, ByteBuffer data) {
        requireOpen();
        sdl.pushUniforms(slot, data);
    }

    /// Runs the bound pipeline over `x` by `y` by `z` workgroups, each of the
    /// code's `numthreads`.
    ///
    /// @throws IllegalStateException    when no pipeline is bound, or its
    ///                                  read-only storage is not
    /// @throws IllegalArgumentException when a count is below one
    public void dispatch(int x, int y, int z) {
        requireOpen();
        sdl.dispatch(x, y, z);
    }

    /// Runs the bound pipeline over `x` workgroups.
    ///
    /// @throws IllegalStateException    as [#dispatch(int, int, int)]
    /// @throws IllegalArgumentException as [#dispatch(int, int, int)]
    public void dispatch(int x) {
        dispatch(x, 1, 1);
    }

    void end() {
        ended = true;
    }

    private void requireOpen() {
        if (ended) {
            throw new IllegalStateException("this compute pass has ended");
        }
    }
}
