package dev.goldberry.natives.sdl.calls;

import static java.lang.foreign.ValueLayout.ADDRESS;
import static java.lang.foreign.ValueLayout.JAVA_INT;

import java.lang.foreign.FunctionDescriptor;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.SymbolLookup;
import java.lang.invoke.MethodHandle;

import dev.goldberry.natives.Downcalls;

/// What a compute pass records -- the pipeline, the storage buffers and
/// textures it reads, the uniforms pushed and the dispatch -- and the one
/// command outside a pass that is compute-shaped: generating a texture's mip
/// chain.
///
/// One holder per function: its handle, its address, and a `call` whose
/// parameters are the C prototype’s. See [Downcalls] for why the handle is a
/// `static final` constant and why these live in a package of their own.
public record SdlGpuComputeCalls(
        BeginGPUComputePass beginGPUComputePass,
        BindGPUComputePipeline bindGPUComputePipeline,
        BindGPUComputeStorageBuffers bindGPUComputeStorageBuffers,
        BindGPUComputeStorageTextures bindGPUComputeStorageTextures,
        PushGPUComputeUniformData pushGPUComputeUniformData,
        DispatchGPUCompute dispatchGPUCompute,
        EndGPUComputePass endGPUComputePass,
        GenerateMipmapsForGPUTexture generateMipmapsForGPUTexture) {

    /// Binds every function above.
    ///
    /// @param lookup the loaded `libgoldberry`
    public static SdlGpuComputeCalls bind(SymbolLookup lookup) {
        return new SdlGpuComputeCalls(
                new BeginGPUComputePass(lookup),
                new BindGPUComputePipeline(lookup),
                new BindGPUComputeStorageBuffers(lookup),
                new BindGPUComputeStorageTextures(lookup),
                new PushGPUComputeUniformData(lookup),
                new DispatchGPUCompute(lookup),
                new EndGPUComputePass(lookup),
                new GenerateMipmapsForGPUTexture(lookup));
    }

    /// Begins a compute pass, naming the storage textures and buffers it
    /// writes.
    ///
    /// `void* SDL_BeginGPUComputePass(void*, void*, Uint32, void*, Uint32)`
    public static final class BeginGPUComputePass {

        private static final MethodHandle FD_SDL_BeginGPUComputePass =
                Downcalls.link(FunctionDescriptor.of(ADDRESS, ADDRESS, ADDRESS, JAVA_INT, ADDRESS, JAVA_INT));

        private final MemorySegment address;

        BeginGPUComputePass(SymbolLookup lookup) {
            this.address = Downcalls.symbol(lookup, "SDL_BeginGPUComputePass");
        }

        /// Calls `SDL_BeginGPUComputePass`.
        ///
        /// @param commandBuffer   the command buffer
        /// @param textureBindings an `SDL_GPUStorageTextureReadWriteBinding*` array
        /// @param textures        how many texture bindings
        /// @param bufferBindings  an `SDL_GPUStorageBufferReadWriteBinding*` array
        /// @param buffers         how many buffer bindings
        /// @return an `SDL_GPUComputePass*`, or NULL on failure
        public MemorySegment call(
                MemorySegment commandBuffer,
                MemorySegment textureBindings,
                int textures,
                MemorySegment bufferBindings,
                int buffers) {
            try {
                return (MemorySegment) FD_SDL_BeginGPUComputePass.invokeExact(
                        address, commandBuffer, textureBindings, textures, bufferBindings, buffers);
            } catch (Throwable t) {
                throw Downcalls.failure("SDL_BeginGPUComputePass", t);
            }
        }
    }

    /// Binds the pipeline the next dispatches run.
    ///
    /// `void SDL_BindGPUComputePipeline(void*, void*)`
    public static final class BindGPUComputePipeline {

        private static final MethodHandle FD_SDL_BindGPUComputePipeline =
                Downcalls.link(FunctionDescriptor.ofVoid(ADDRESS, ADDRESS));

        private final MemorySegment address;

        BindGPUComputePipeline(SymbolLookup lookup) {
            this.address = Downcalls.symbol(lookup, "SDL_BindGPUComputePipeline");
        }

        /// Calls `SDL_BindGPUComputePipeline`.
        ///
        /// @param computePass the pass
        /// @param pipeline    the pipeline
        public void call(MemorySegment computePass, MemorySegment pipeline) {
            try {
                FD_SDL_BindGPUComputePipeline.invokeExact(address, computePass, pipeline);
            } catch (Throwable t) {
                throw Downcalls.failure("SDL_BindGPUComputePipeline", t);
            }
        }
    }

    /// Binds storage buffers the compute shader reads.
    ///
    /// `void SDL_BindGPUComputeStorageBuffers(void*, Uint32, void*, Uint32)`
    public static final class BindGPUComputeStorageBuffers {

        private static final MethodHandle FD_SDL_BindGPUComputeStorageBuffers =
                Downcalls.link(FunctionDescriptor.ofVoid(ADDRESS, JAVA_INT, ADDRESS, JAVA_INT));

        private final MemorySegment address;

        BindGPUComputeStorageBuffers(SymbolLookup lookup) {
            this.address = Downcalls.symbol(lookup, "SDL_BindGPUComputeStorageBuffers");
        }

        /// Calls `SDL_BindGPUComputeStorageBuffers`.
        ///
        /// @param computePass the pass
        /// @param firstSlot   the first slot bound
        /// @param buffers     an `SDL_GPUBuffer**` array
        /// @param count       how many
        public void call(MemorySegment computePass, int firstSlot, MemorySegment buffers, int count) {
            try {
                FD_SDL_BindGPUComputeStorageBuffers.invokeExact(address, computePass, firstSlot, buffers, count);
            } catch (Throwable t) {
                throw Downcalls.failure("SDL_BindGPUComputeStorageBuffers", t);
            }
        }
    }

    /// Binds storage textures the compute shader reads.
    ///
    /// `void SDL_BindGPUComputeStorageTextures(void*, Uint32, void*, Uint32)`
    public static final class BindGPUComputeStorageTextures {

        private static final MethodHandle FD_SDL_BindGPUComputeStorageTextures =
                Downcalls.link(FunctionDescriptor.ofVoid(ADDRESS, JAVA_INT, ADDRESS, JAVA_INT));

        private final MemorySegment address;

        BindGPUComputeStorageTextures(SymbolLookup lookup) {
            this.address = Downcalls.symbol(lookup, "SDL_BindGPUComputeStorageTextures");
        }

        /// Calls `SDL_BindGPUComputeStorageTextures`.
        ///
        /// @param computePass the pass
        /// @param firstSlot   the first slot bound
        /// @param textures    an `SDL_GPUTexture**` array
        /// @param count       how many
        public void call(MemorySegment computePass, int firstSlot, MemorySegment textures, int count) {
            try {
                FD_SDL_BindGPUComputeStorageTextures.invokeExact(address, computePass, firstSlot, textures, count);
            } catch (Throwable t) {
                throw Downcalls.failure("SDL_BindGPUComputeStorageTextures", t);
            }
        }
    }

    /// Pushes uniform data the compute shader reads.
    ///
    /// `void SDL_PushGPUComputeUniformData(void*, Uint32, void*, Uint32)`
    public static final class PushGPUComputeUniformData {

        private static final MethodHandle FD_SDL_PushGPUComputeUniformData =
                Downcalls.link(FunctionDescriptor.ofVoid(ADDRESS, JAVA_INT, ADDRESS, JAVA_INT));

        private final MemorySegment address;

        PushGPUComputeUniformData(SymbolLookup lookup) {
            this.address = Downcalls.symbol(lookup, "SDL_PushGPUComputeUniformData");
        }

        /// Calls `SDL_PushGPUComputeUniformData`.
        ///
        /// @param commandBuffer the command buffer
        /// @param slot          the uniform slot
        /// @param data          the bytes, copied
        /// @param length        how many
        public void call(MemorySegment commandBuffer, int slot, MemorySegment data, int length) {
            try {
                FD_SDL_PushGPUComputeUniformData.invokeExact(address, commandBuffer, slot, data, length);
            } catch (Throwable t) {
                throw Downcalls.failure("SDL_PushGPUComputeUniformData", t);
            }
        }
    }

    /// Runs the bound pipeline over a grid of workgroups.
    ///
    /// `void SDL_DispatchGPUCompute(void*, Uint32, Uint32, Uint32)`
    public static final class DispatchGPUCompute {

        private static final MethodHandle FD_SDL_DispatchGPUCompute =
                Downcalls.link(FunctionDescriptor.ofVoid(ADDRESS, JAVA_INT, JAVA_INT, JAVA_INT));

        private final MemorySegment address;

        DispatchGPUCompute(SymbolLookup lookup) {
            this.address = Downcalls.symbol(lookup, "SDL_DispatchGPUCompute");
        }

        /// Calls `SDL_DispatchGPUCompute`.
        ///
        /// @param computePass the pass
        /// @param groupsX     workgroups along x
        /// @param groupsY     workgroups along y
        /// @param groupsZ     workgroups along z
        public void call(MemorySegment computePass, int groupsX, int groupsY, int groupsZ) {
            try {
                FD_SDL_DispatchGPUCompute.invokeExact(address, computePass, groupsX, groupsY, groupsZ);
            } catch (Throwable t) {
                throw Downcalls.failure("SDL_DispatchGPUCompute", t);
            }
        }
    }

    /// Ends a compute pass.
    ///
    /// `void SDL_EndGPUComputePass(void*)`
    public static final class EndGPUComputePass {

        private static final MethodHandle FD_SDL_EndGPUComputePass = Downcalls.link(FunctionDescriptor.ofVoid(ADDRESS));

        private final MemorySegment address;

        EndGPUComputePass(SymbolLookup lookup) {
            this.address = Downcalls.symbol(lookup, "SDL_EndGPUComputePass");
        }

        /// Calls `SDL_EndGPUComputePass`.
        ///
        /// @param computePass the pass
        public void call(MemorySegment computePass) {
            try {
                FD_SDL_EndGPUComputePass.invokeExact(address, computePass);
            } catch (Throwable t) {
                throw Downcalls.failure("SDL_EndGPUComputePass", t);
            }
        }
    }

    /// Fills every mip level of a texture below the first from the level above
    /// it. Recorded outside any pass.
    ///
    /// `void SDL_GenerateMipmapsForGPUTexture(void*, void*)`
    public static final class GenerateMipmapsForGPUTexture {

        private static final MethodHandle FD_SDL_GenerateMipmapsForGPUTexture =
                Downcalls.link(FunctionDescriptor.ofVoid(ADDRESS, ADDRESS));

        private final MemorySegment address;

        GenerateMipmapsForGPUTexture(SymbolLookup lookup) {
            this.address = Downcalls.symbol(lookup, "SDL_GenerateMipmapsForGPUTexture");
        }

        /// Calls `SDL_GenerateMipmapsForGPUTexture`.
        ///
        /// @param commandBuffer the command buffer
        /// @param texture       a texture with more than one mip level
        public void call(MemorySegment commandBuffer, MemorySegment texture) {
            try {
                FD_SDL_GenerateMipmapsForGPUTexture.invokeExact(address, commandBuffer, texture);
            } catch (Throwable t) {
                throw Downcalls.failure("SDL_GenerateMipmapsForGPUTexture", t);
            }
        }
    }
}
