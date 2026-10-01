package dev.goldberry.natives.sdl.calls;

import static java.lang.foreign.ValueLayout.ADDRESS;
import static java.lang.foreign.ValueLayout.JAVA_BOOLEAN;
import static java.lang.foreign.ValueLayout.JAVA_INT;

import java.lang.foreign.FunctionDescriptor;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.SymbolLookup;
import java.lang.invoke.MethodHandle;

import dev.goldberry.natives.Downcalls;

/// GPU buffers: vertices and indices, how they get there, and the draws that
/// read them (`docs/gpu-plan.md`, phase 2, for `canvas3d`).
///
/// One holder per function: its handle, its address, and a `call` whose
/// parameters are the C prototype’s. See [Downcalls] for why the handle is a
/// `static final` constant and why these live in a package of their own.
public record SdlGpuBufferCalls(
        CreateGPUBuffer createGPUBuffer,
        ReleaseGPUBuffer releaseGPUBuffer,
        UploadToGPUBuffer uploadToGPUBuffer,
        DownloadFromGPUBuffer downloadFromGPUBuffer,
        BindGPUVertexBuffers bindGPUVertexBuffers,
        BindGPUIndexBuffer bindGPUIndexBuffer,
        DrawGPUIndexedPrimitives drawGPUIndexedPrimitives) {

    /// Binds every function above.
    ///
    /// @param lookup the loaded `libgoldberry`
    public static SdlGpuBufferCalls bind(SymbolLookup lookup) {
        return new SdlGpuBufferCalls(
                new CreateGPUBuffer(lookup),
                new ReleaseGPUBuffer(lookup),
                new UploadToGPUBuffer(lookup),
                new DownloadFromGPUBuffer(lookup),
                new BindGPUVertexBuffers(lookup),
                new BindGPUIndexBuffer(lookup),
                new DrawGPUIndexedPrimitives(lookup));
    }

    /// Creates a buffer.
    ///
    /// `void* SDL_CreateGPUBuffer(void*, void*)`
    public static final class CreateGPUBuffer {

        private static final MethodHandle FD_SDL_CreateGPUBuffer =
                Downcalls.link(FunctionDescriptor.of(ADDRESS, ADDRESS, ADDRESS));

        private final MemorySegment address;

        CreateGPUBuffer(SymbolLookup lookup) {
            this.address = Downcalls.symbol(lookup, "SDL_CreateGPUBuffer");
        }

        /// Calls `SDL_CreateGPUBuffer`.
        ///
        /// @param device     the device
        /// @param createInfo an `SDL_GPUBufferCreateInfo*`
        /// @return an `SDL_GPUBuffer*`, or NULL on failure
        public MemorySegment call(MemorySegment device, MemorySegment createInfo) {
            try {
                return (MemorySegment) FD_SDL_CreateGPUBuffer.invokeExact(address, device, createInfo);
            } catch (Throwable t) {
                throw Downcalls.failure("SDL_CreateGPUBuffer", t);
            }
        }
    }

    /// Releases a buffer. SDL frees it once no submitted command still uses it.
    ///
    /// `void SDL_ReleaseGPUBuffer(void*, void*)`
    public static final class ReleaseGPUBuffer {

        private static final MethodHandle FD_SDL_ReleaseGPUBuffer =
                Downcalls.link(FunctionDescriptor.ofVoid(ADDRESS, ADDRESS));

        private final MemorySegment address;

        ReleaseGPUBuffer(SymbolLookup lookup) {
            this.address = Downcalls.symbol(lookup, "SDL_ReleaseGPUBuffer");
        }

        /// Calls `SDL_ReleaseGPUBuffer`.
        ///
        /// @param device the device
        /// @param buffer the buffer
        public void call(MemorySegment device, MemorySegment buffer) {
            try {
                FD_SDL_ReleaseGPUBuffer.invokeExact(address, device, buffer);
            } catch (Throwable t) {
                throw Downcalls.failure("SDL_ReleaseGPUBuffer", t);
            }
        }
    }

    /// Records a copy from a transfer buffer into a buffer, in a copy pass.
    ///
    /// `void SDL_UploadToGPUBuffer(void*, void*, void*, bool)`
    public static final class UploadToGPUBuffer {

        private static final MethodHandle FD_SDL_UploadToGPUBuffer =
                Downcalls.link(FunctionDescriptor.ofVoid(ADDRESS, ADDRESS, ADDRESS, JAVA_BOOLEAN));

        private final MemorySegment address;

        UploadToGPUBuffer(SymbolLookup lookup) {
            this.address = Downcalls.symbol(lookup, "SDL_UploadToGPUBuffer");
        }

        /// Calls `SDL_UploadToGPUBuffer`.
        ///
        /// @param copyPass    the pass
        /// @param source      an `SDL_GPUTransferBufferLocation*`
        /// @param destination an `SDL_GPUBufferRegion*`
        /// @param cycle       take fresh buffer memory if the GPU still reads the
        ///                    old
        public void call(MemorySegment copyPass, MemorySegment source, MemorySegment destination, boolean cycle) {
            try {
                FD_SDL_UploadToGPUBuffer.invokeExact(address, copyPass, source, destination, cycle);
            } catch (Throwable t) {
                throw Downcalls.failure("SDL_UploadToGPUBuffer", t);
            }
        }
    }

    /// Records a copy from a buffer into a transfer buffer, in a copy pass.
    ///
    /// `void SDL_DownloadFromGPUBuffer(void*, void*, void*)`
    public static final class DownloadFromGPUBuffer {

        private static final MethodHandle FD_SDL_DownloadFromGPUBuffer =
                Downcalls.link(FunctionDescriptor.ofVoid(ADDRESS, ADDRESS, ADDRESS));

        private final MemorySegment address;

        DownloadFromGPUBuffer(SymbolLookup lookup) {
            this.address = Downcalls.symbol(lookup, "SDL_DownloadFromGPUBuffer");
        }

        /// Calls `SDL_DownloadFromGPUBuffer`.
        ///
        /// @param copyPass    the pass
        /// @param source      an `SDL_GPUBufferRegion*`
        /// @param destination an `SDL_GPUTransferBufferLocation*`
        public void call(MemorySegment copyPass, MemorySegment source, MemorySegment destination) {
            try {
                FD_SDL_DownloadFromGPUBuffer.invokeExact(address, copyPass, source, destination);
            } catch (Throwable t) {
                throw Downcalls.failure("SDL_DownloadFromGPUBuffer", t);
            }
        }
    }

    /// Binds vertex buffers to the pipeline's slots, for the draws that follow.
    ///
    /// `void SDL_BindGPUVertexBuffers(void*, uint32_t, void*, uint32_t)`
    public static final class BindGPUVertexBuffers {

        private static final MethodHandle FD_SDL_BindGPUVertexBuffers =
                Downcalls.link(FunctionDescriptor.ofVoid(ADDRESS, JAVA_INT, ADDRESS, JAVA_INT));

        private final MemorySegment address;

        BindGPUVertexBuffers(SymbolLookup lookup) {
            this.address = Downcalls.symbol(lookup, "SDL_BindGPUVertexBuffers");
        }

        /// Calls `SDL_BindGPUVertexBuffers`.
        ///
        /// @param renderPass the pass
        /// @param firstSlot  the first slot bound
        /// @param bindings   an `SDL_GPUBufferBinding*` array
        /// @param count      how many bindings
        public void call(MemorySegment renderPass, int firstSlot, MemorySegment bindings, int count) {
            try {
                FD_SDL_BindGPUVertexBuffers.invokeExact(address, renderPass, firstSlot, bindings, count);
            } catch (Throwable t) {
                throw Downcalls.failure("SDL_BindGPUVertexBuffers", t);
            }
        }
    }

    /// Binds the index buffer the next indexed draws read.
    ///
    /// `void SDL_BindGPUIndexBuffer(void*, void*, int)`
    public static final class BindGPUIndexBuffer {

        private static final MethodHandle FD_SDL_BindGPUIndexBuffer =
                Downcalls.link(FunctionDescriptor.ofVoid(ADDRESS, ADDRESS, JAVA_INT));

        private final MemorySegment address;

        BindGPUIndexBuffer(SymbolLookup lookup) {
            this.address = Downcalls.symbol(lookup, "SDL_BindGPUIndexBuffer");
        }

        /// Calls `SDL_BindGPUIndexBuffer`.
        ///
        /// @param renderPass the pass
        /// @param binding    an `SDL_GPUBufferBinding*`
        /// @param indexSize  an `SDL_GPUIndexElementSize`
        public void call(MemorySegment renderPass, MemorySegment binding, int indexSize) {
            try {
                FD_SDL_BindGPUIndexBuffer.invokeExact(address, renderPass, binding, indexSize);
            } catch (Throwable t) {
                throw Downcalls.failure("SDL_BindGPUIndexBuffer", t);
            }
        }
    }

    /// Draws with vertices picked by the bound index buffer.
    ///
    /// `void SDL_DrawGPUIndexedPrimitives(void*, uint32_t, uint32_t, uint32_t, int32_t, uint32_t)`
    public static final class DrawGPUIndexedPrimitives {

        private static final MethodHandle FD_SDL_DrawGPUIndexedPrimitives =
                Downcalls.link(FunctionDescriptor.ofVoid(ADDRESS, JAVA_INT, JAVA_INT, JAVA_INT, JAVA_INT, JAVA_INT));

        private final MemorySegment address;

        DrawGPUIndexedPrimitives(SymbolLookup lookup) {
            this.address = Downcalls.symbol(lookup, "SDL_DrawGPUIndexedPrimitives");
        }

        /// Calls `SDL_DrawGPUIndexedPrimitives`.
        ///
        /// @param renderPass    the pass
        /// @param indices       how many indices
        /// @param instances     how many instances
        /// @param firstIndex    the first index read
        /// @param vertexOffset  added to every index before the vertex is fetched
        /// @param firstInstance the first instance id
        public void call(
                MemorySegment renderPass,
                int indices,
                int instances,
                int firstIndex,
                int vertexOffset,
                int firstInstance) {
            try {
                FD_SDL_DrawGPUIndexedPrimitives.invokeExact(
                        address, renderPass, indices, instances, firstIndex, vertexOffset, firstInstance);
            } catch (Throwable t) {
                throw Downcalls.failure("SDL_DrawGPUIndexedPrimitives", t);
            }
        }
    }
}
