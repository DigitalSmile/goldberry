package dev.goldberry.natives.sdl.calls;

import static java.lang.foreign.ValueLayout.ADDRESS;
import static java.lang.foreign.ValueLayout.JAVA_BOOLEAN;
import static java.lang.foreign.ValueLayout.JAVA_INT;

import java.lang.foreign.FunctionDescriptor;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.SymbolLookup;
import java.lang.invoke.MethodHandle;

import dev.goldberry.natives.Downcalls;

/// A GPU device's commands: command buffers, the passes recorded into them, and
/// the fences their submission returns.
///
/// One holder per function: its handle, its address, and a `call` whose
/// parameters are the C prototype’s. See [Downcalls] for why the handle is a
/// `static final` constant and why these live in a package of their own.
public record SdlGpuCommandCalls(
        AcquireGPUCommandBuffer acquireGPUCommandBuffer,
        SubmitGPUCommandBuffer submitGPUCommandBuffer,
        SubmitGPUCommandBufferAndAcquireFence submitGPUCommandBufferAndAcquireFence,
        CancelGPUCommandBuffer cancelGPUCommandBuffer,
        WaitForGPUFences waitForGPUFences,
        ReleaseGPUFence releaseGPUFence,
        BeginGPURenderPass beginGPURenderPass,
        EndGPURenderPass endGPURenderPass,
        BeginGPUCopyPass beginGPUCopyPass,
        UploadToGPUTexture uploadToGPUTexture,
        DownloadFromGPUTexture downloadFromGPUTexture,
        EndGPUCopyPass endGPUCopyPass,
        BlitGPUTexture blitGPUTexture) {

    /// Binds every function above.
    ///
    /// @param lookup the loaded `libgoldberry`
    public static SdlGpuCommandCalls bind(SymbolLookup lookup) {
        return new SdlGpuCommandCalls(
                new AcquireGPUCommandBuffer(lookup),
                new SubmitGPUCommandBuffer(lookup),
                new SubmitGPUCommandBufferAndAcquireFence(lookup),
                new CancelGPUCommandBuffer(lookup),
                new WaitForGPUFences(lookup),
                new ReleaseGPUFence(lookup),
                new BeginGPURenderPass(lookup),
                new EndGPURenderPass(lookup),
                new BeginGPUCopyPass(lookup),
                new UploadToGPUTexture(lookup),
                new DownloadFromGPUTexture(lookup),
                new EndGPUCopyPass(lookup),
                new BlitGPUTexture(lookup));
    }

    /// `SDL_GPU_LOADOP_LOAD`: a render pass keeps what the target held.
    public static final int LOADOP_LOAD = 0;

    /// `SDL_GPU_LOADOP_CLEAR`: a render pass clears the target first.
    public static final int LOADOP_CLEAR = 1;

    /// `SDL_GPU_LOADOP_DONT_CARE`: the target's old contents are undefined.
    public static final int LOADOP_DONT_CARE = 2;

    /// `SDL_GPU_STOREOP_STORE`: what a render pass drew is kept.
    public static final int STOREOP_STORE = 0;

    /// `SDL_GPU_STOREOP_DONT_CARE`: what a render pass drew may be discarded.
    public static final int STOREOP_DONT_CARE = 1;

    /// `SDL_GPU_FILTER_NEAREST`: a blit that samples the nearest texel.
    public static final int FILTER_NEAREST = 0;

    /// `SDL_GPU_FILTER_LINEAR`: a blit that interpolates.
    public static final int FILTER_LINEAR = 1;

    /// `SDL_FLIP_NONE`: a blit that does not mirror.
    public static final int FLIP_NONE = 0;

    /// Acquires a command buffer to record passes into.
    ///
    /// `void* SDL_AcquireGPUCommandBuffer(void*)`
    public static final class AcquireGPUCommandBuffer {

        private static final MethodHandle FD_SDL_AcquireGPUCommandBuffer =
                Downcalls.link(FunctionDescriptor.of(ADDRESS, ADDRESS));

        private final MemorySegment address;

        AcquireGPUCommandBuffer(SymbolLookup lookup) {
            this.address = Downcalls.symbol(lookup, "SDL_AcquireGPUCommandBuffer");
        }

        /// Calls `SDL_AcquireGPUCommandBuffer`.
        ///
        /// @param device the device
        /// @return an `SDL_GPUCommandBuffer*`, or NULL on failure
        public MemorySegment call(MemorySegment device) {
            try {
                return (MemorySegment) FD_SDL_AcquireGPUCommandBuffer.invokeExact(address, device);
            } catch (Throwable t) {
                throw Downcalls.failure("SDL_AcquireGPUCommandBuffer", t);
            }
        }
    }

    /// Submits a command buffer. It must not be used after.
    ///
    /// `_Bool SDL_SubmitGPUCommandBuffer(void*)`
    public static final class SubmitGPUCommandBuffer {

        private static final MethodHandle FD_SDL_SubmitGPUCommandBuffer =
                Downcalls.link(FunctionDescriptor.of(JAVA_BOOLEAN, ADDRESS));

        private final MemorySegment address;

        SubmitGPUCommandBuffer(SymbolLookup lookup) {
            this.address = Downcalls.symbol(lookup, "SDL_SubmitGPUCommandBuffer");
        }

        /// Calls `SDL_SubmitGPUCommandBuffer`.
        ///
        /// @param commandBuffer the command buffer
        /// @return false if SDL refused
        public boolean call(MemorySegment commandBuffer) {
            try {
                return (boolean) FD_SDL_SubmitGPUCommandBuffer.invokeExact(address, commandBuffer);
            } catch (Throwable t) {
                throw Downcalls.failure("SDL_SubmitGPUCommandBuffer", t);
            }
        }
    }

    /// Submits a command buffer and returns a fence that signals when the GPU has
    /// finished it: how a readback knows its pixels have arrived.
    ///
    /// `void* SDL_SubmitGPUCommandBufferAndAcquireFence(void*)`
    public static final class SubmitGPUCommandBufferAndAcquireFence {

        private static final MethodHandle FD_SDL_SubmitGPUCommandBufferAndAcquireFence =
                Downcalls.link(FunctionDescriptor.of(ADDRESS, ADDRESS));

        private final MemorySegment address;

        SubmitGPUCommandBufferAndAcquireFence(SymbolLookup lookup) {
            this.address = Downcalls.symbol(lookup, "SDL_SubmitGPUCommandBufferAndAcquireFence");
        }

        /// Calls `SDL_SubmitGPUCommandBufferAndAcquireFence`.
        ///
        /// @param commandBuffer the command buffer
        /// @return an `SDL_GPUFence*`, or NULL on failure
        public MemorySegment call(MemorySegment commandBuffer) {
            try {
                return (MemorySegment) FD_SDL_SubmitGPUCommandBufferAndAcquireFence.invokeExact(address, commandBuffer);
            } catch (Throwable t) {
                throw Downcalls.failure("SDL_SubmitGPUCommandBufferAndAcquireFence", t);
            }
        }
    }

    /// Discards a command buffer that has not been submitted.
    ///
    /// `_Bool SDL_CancelGPUCommandBuffer(void*)`
    public static final class CancelGPUCommandBuffer {

        private static final MethodHandle FD_SDL_CancelGPUCommandBuffer =
                Downcalls.link(FunctionDescriptor.of(JAVA_BOOLEAN, ADDRESS));

        private final MemorySegment address;

        CancelGPUCommandBuffer(SymbolLookup lookup) {
            this.address = Downcalls.symbol(lookup, "SDL_CancelGPUCommandBuffer");
        }

        /// Calls `SDL_CancelGPUCommandBuffer`.
        ///
        /// @param commandBuffer the command buffer
        /// @return false if SDL refused
        public boolean call(MemorySegment commandBuffer) {
            try {
                return (boolean) FD_SDL_CancelGPUCommandBuffer.invokeExact(address, commandBuffer);
            } catch (Throwable t) {
                throw Downcalls.failure("SDL_CancelGPUCommandBuffer", t);
            }
        }
    }

    /// Blocks until fences signal.
    ///
    /// `_Bool SDL_WaitForGPUFences(void*, _Bool, void*, uint32_t)`
    public static final class WaitForGPUFences {

        private static final MethodHandle FD_SDL_WaitForGPUFences =
                Downcalls.link(FunctionDescriptor.of(JAVA_BOOLEAN, ADDRESS, JAVA_BOOLEAN, ADDRESS, JAVA_INT));

        private final MemorySegment address;

        WaitForGPUFences(SymbolLookup lookup) {
            this.address = Downcalls.symbol(lookup, "SDL_WaitForGPUFences");
        }

        /// Calls `SDL_WaitForGPUFences`.
        ///
        /// @param device  the device
        /// @param waitAll whether to wait for all, or for any
        /// @param fences  an `SDL_GPUFence**` array
        /// @param count   how many fences
        /// @return false on failure
        public boolean call(MemorySegment device, boolean waitAll, MemorySegment fences, int count) {
            try {
                return (boolean) FD_SDL_WaitForGPUFences.invokeExact(address, device, waitAll, fences, count);
            } catch (Throwable t) {
                throw Downcalls.failure("SDL_WaitForGPUFences", t);
            }
        }
    }

    /// Releases a fence.
    ///
    /// `void SDL_ReleaseGPUFence(void*, void*)`
    public static final class ReleaseGPUFence {

        private static final MethodHandle FD_SDL_ReleaseGPUFence =
                Downcalls.link(FunctionDescriptor.ofVoid(ADDRESS, ADDRESS));

        private final MemorySegment address;

        ReleaseGPUFence(SymbolLookup lookup) {
            this.address = Downcalls.symbol(lookup, "SDL_ReleaseGPUFence");
        }

        /// Calls `SDL_ReleaseGPUFence`.
        ///
        /// @param device the device
        /// @param fence  the fence
        public void call(MemorySegment device, MemorySegment fence) {
            try {
                FD_SDL_ReleaseGPUFence.invokeExact(address, device, fence);
            } catch (Throwable t) {
                throw Downcalls.failure("SDL_ReleaseGPUFence", t);
            }
        }
    }

    /// Begins a render pass. Its load operation is what clears a target.
    ///
    /// `void* SDL_BeginGPURenderPass(void*, void*, uint32_t, void*)`
    public static final class BeginGPURenderPass {

        private static final MethodHandle FD_SDL_BeginGPURenderPass =
                Downcalls.link(FunctionDescriptor.of(ADDRESS, ADDRESS, ADDRESS, JAVA_INT, ADDRESS));

        private final MemorySegment address;

        BeginGPURenderPass(SymbolLookup lookup) {
            this.address = Downcalls.symbol(lookup, "SDL_BeginGPURenderPass");
        }

        /// Calls `SDL_BeginGPURenderPass`.
        ///
        /// @param commandBuffer      the command buffer
        /// @param colorTargets       an `SDL_GPUColorTargetInfo*` array
        /// @param count              how many colour targets
        /// @param depthStencilTarget an `SDL_GPUDepthStencilTargetInfo*`, or NULL
        /// @return an `SDL_GPURenderPass*`
        public MemorySegment call(
                MemorySegment commandBuffer, MemorySegment colorTargets, int count, MemorySegment depthStencilTarget) {
            try {
                return (MemorySegment) FD_SDL_BeginGPURenderPass.invokeExact(
                        address, commandBuffer, colorTargets, count, depthStencilTarget);
            } catch (Throwable t) {
                throw Downcalls.failure("SDL_BeginGPURenderPass", t);
            }
        }
    }

    /// Ends a render pass.
    ///
    /// `void SDL_EndGPURenderPass(void*)`
    public static final class EndGPURenderPass {

        private static final MethodHandle FD_SDL_EndGPURenderPass = Downcalls.link(FunctionDescriptor.ofVoid(ADDRESS));

        private final MemorySegment address;

        EndGPURenderPass(SymbolLookup lookup) {
            this.address = Downcalls.symbol(lookup, "SDL_EndGPURenderPass");
        }

        /// Calls `SDL_EndGPURenderPass`.
        ///
        /// @param renderPass the pass
        public void call(MemorySegment renderPass) {
            try {
                FD_SDL_EndGPURenderPass.invokeExact(address, renderPass);
            } catch (Throwable t) {
                throw Downcalls.failure("SDL_EndGPURenderPass", t);
            }
        }
    }

    /// Begins a copy pass, in which uploads and downloads are recorded.
    ///
    /// `void* SDL_BeginGPUCopyPass(void*)`
    public static final class BeginGPUCopyPass {

        private static final MethodHandle FD_SDL_BeginGPUCopyPass =
                Downcalls.link(FunctionDescriptor.of(ADDRESS, ADDRESS));

        private final MemorySegment address;

        BeginGPUCopyPass(SymbolLookup lookup) {
            this.address = Downcalls.symbol(lookup, "SDL_BeginGPUCopyPass");
        }

        /// Calls `SDL_BeginGPUCopyPass`.
        ///
        /// @param commandBuffer the command buffer
        /// @return an `SDL_GPUCopyPass*`
        public MemorySegment call(MemorySegment commandBuffer) {
            try {
                return (MemorySegment) FD_SDL_BeginGPUCopyPass.invokeExact(address, commandBuffer);
            } catch (Throwable t) {
                throw Downcalls.failure("SDL_BeginGPUCopyPass", t);
            }
        }
    }

    /// Records a copy from a transfer buffer into a texture region.
    ///
    /// `void SDL_UploadToGPUTexture(void*, void*, void*, _Bool)`
    public static final class UploadToGPUTexture {

        private static final MethodHandle FD_SDL_UploadToGPUTexture =
                Downcalls.link(FunctionDescriptor.ofVoid(ADDRESS, ADDRESS, ADDRESS, JAVA_BOOLEAN));

        private final MemorySegment address;

        UploadToGPUTexture(SymbolLookup lookup) {
            this.address = Downcalls.symbol(lookup, "SDL_UploadToGPUTexture");
        }

        /// Calls `SDL_UploadToGPUTexture`.
        ///
        /// @param copyPass    the pass
        /// @param source      an `SDL_GPUTextureTransferInfo*`
        /// @param destination an `SDL_GPUTextureRegion*`
        /// @param cycle       whether to write into fresh memory if the GPU still reads the old
        public void call(MemorySegment copyPass, MemorySegment source, MemorySegment destination, boolean cycle) {
            try {
                FD_SDL_UploadToGPUTexture.invokeExact(address, copyPass, source, destination, cycle);
            } catch (Throwable t) {
                throw Downcalls.failure("SDL_UploadToGPUTexture", t);
            }
        }
    }

    /// Records a copy from a texture region into a transfer buffer. The pixels are
    /// there once the command buffer's fence has signalled.
    ///
    /// `void SDL_DownloadFromGPUTexture(void*, void*, void*)`
    public static final class DownloadFromGPUTexture {

        private static final MethodHandle FD_SDL_DownloadFromGPUTexture =
                Downcalls.link(FunctionDescriptor.ofVoid(ADDRESS, ADDRESS, ADDRESS));

        private final MemorySegment address;

        DownloadFromGPUTexture(SymbolLookup lookup) {
            this.address = Downcalls.symbol(lookup, "SDL_DownloadFromGPUTexture");
        }

        /// Calls `SDL_DownloadFromGPUTexture`.
        ///
        /// @param copyPass    the pass
        /// @param source      an `SDL_GPUTextureRegion*`
        /// @param destination an `SDL_GPUTextureTransferInfo*`
        public void call(MemorySegment copyPass, MemorySegment source, MemorySegment destination) {
            try {
                FD_SDL_DownloadFromGPUTexture.invokeExact(address, copyPass, source, destination);
            } catch (Throwable t) {
                throw Downcalls.failure("SDL_DownloadFromGPUTexture", t);
            }
        }
    }

    /// Ends a copy pass.
    ///
    /// `void SDL_EndGPUCopyPass(void*)`
    public static final class EndGPUCopyPass {

        private static final MethodHandle FD_SDL_EndGPUCopyPass = Downcalls.link(FunctionDescriptor.ofVoid(ADDRESS));

        private final MemorySegment address;

        EndGPUCopyPass(SymbolLookup lookup) {
            this.address = Downcalls.symbol(lookup, "SDL_EndGPUCopyPass");
        }

        /// Calls `SDL_EndGPUCopyPass`.
        ///
        /// @param copyPass the pass
        public void call(MemorySegment copyPass) {
            try {
                FD_SDL_EndGPUCopyPass.invokeExact(address, copyPass);
            } catch (Throwable t) {
                throw Downcalls.failure("SDL_EndGPUCopyPass", t);
            }
        }
    }

    /// Records a scaled, filtered copy from one texture region to another: how a
    /// texture reaches the swapchain before any shader of the toolkit's exists.
    ///
    /// `void SDL_BlitGPUTexture(void*, void*)`
    public static final class BlitGPUTexture {

        private static final MethodHandle FD_SDL_BlitGPUTexture =
                Downcalls.link(FunctionDescriptor.ofVoid(ADDRESS, ADDRESS));

        private final MemorySegment address;

        BlitGPUTexture(SymbolLookup lookup) {
            this.address = Downcalls.symbol(lookup, "SDL_BlitGPUTexture");
        }

        /// Calls `SDL_BlitGPUTexture`.
        ///
        /// @param commandBuffer the command buffer, outside any pass
        /// @param info          an `SDL_GPUBlitInfo*`
        public void call(MemorySegment commandBuffer, MemorySegment info) {
            try {
                FD_SDL_BlitGPUTexture.invokeExact(address, commandBuffer, info);
            } catch (Throwable t) {
                throw Downcalls.failure("SDL_BlitGPUTexture", t);
            }
        }
    }
}
