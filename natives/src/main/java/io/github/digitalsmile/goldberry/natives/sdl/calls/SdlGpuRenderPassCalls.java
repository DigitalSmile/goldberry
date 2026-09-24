package io.github.digitalsmile.goldberry.natives.sdl.calls;

import static java.lang.foreign.ValueLayout.ADDRESS;
import static java.lang.foreign.ValueLayout.JAVA_INT;

import java.lang.foreign.FunctionDescriptor;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.SymbolLookup;
import java.lang.invoke.MethodHandle;

import io.github.digitalsmile.goldberry.natives.Downcalls;

/// What a render pass records: the pipeline, the viewport and scissor, the
/// textures sampled, the uniforms pushed, and the draws.
///
/// One holder per function: its handle, its address, and a `call` whose
/// parameters are the C prototype’s. See [Downcalls] for why the handle is a
/// `static final` constant and why these live in a package of their own.
public record SdlGpuRenderPassCalls(
        BindGPUGraphicsPipeline bindGPUGraphicsPipeline,
        SetGPUViewport setGPUViewport,
        SetGPUScissor setGPUScissor,
        BindGPUFragmentSamplers bindGPUFragmentSamplers,
        DrawGPUPrimitives drawGPUPrimitives,
        PushGPUVertexUniformData pushGPUVertexUniformData,
        PushGPUFragmentUniformData pushGPUFragmentUniformData) {

    /// Binds every function above.
    ///
    /// @param lookup the loaded `libgoldberry`
    public static SdlGpuRenderPassCalls bind(SymbolLookup lookup) {
        return new SdlGpuRenderPassCalls(
                new BindGPUGraphicsPipeline(lookup),
                new SetGPUViewport(lookup),
                new SetGPUScissor(lookup),
                new BindGPUFragmentSamplers(lookup),
                new DrawGPUPrimitives(lookup),
                new PushGPUVertexUniformData(lookup),
                new PushGPUFragmentUniformData(lookup));
    }

    /// Binds the pipeline the next draws use.
    ///
    /// `void SDL_BindGPUGraphicsPipeline(void*, void*)`
    public static final class BindGPUGraphicsPipeline {

        private static final MethodHandle FD_SDL_BindGPUGraphicsPipeline =
                Downcalls.link(FunctionDescriptor.ofVoid(ADDRESS, ADDRESS));

        private final MemorySegment address;

        BindGPUGraphicsPipeline(SymbolLookup lookup) {
            this.address = Downcalls.symbol(lookup, "SDL_BindGPUGraphicsPipeline");
        }

        /// Calls `SDL_BindGPUGraphicsPipeline`.
        ///
        /// @param renderPass the pass
        /// @param pipeline   the pipeline
        public void call(MemorySegment renderPass, MemorySegment pipeline) {
            try {
                FD_SDL_BindGPUGraphicsPipeline.invokeExact(address, renderPass, pipeline);
            } catch (Throwable t) {
                throw Downcalls.failure("SDL_BindGPUGraphicsPipeline", t);
            }
        }
    }

    /// Sets the viewport the next draws map clip space onto.
    ///
    /// `void SDL_SetGPUViewport(void*, void*)`
    public static final class SetGPUViewport {

        private static final MethodHandle FD_SDL_SetGPUViewport =
                Downcalls.link(FunctionDescriptor.ofVoid(ADDRESS, ADDRESS));

        private final MemorySegment address;

        SetGPUViewport(SymbolLookup lookup) {
            this.address = Downcalls.symbol(lookup, "SDL_SetGPUViewport");
        }

        /// Calls `SDL_SetGPUViewport`.
        ///
        /// @param renderPass the pass
        /// @param viewport   an `SDL_GPUViewport*`
        public void call(MemorySegment renderPass, MemorySegment viewport) {
            try {
                FD_SDL_SetGPUViewport.invokeExact(address, renderPass, viewport);
            } catch (Throwable t) {
                throw Downcalls.failure("SDL_SetGPUViewport", t);
            }
        }
    }

    /// Sets the rectangle outside which the next draws write nothing: a GPU\nlayer's clip (`docs/gpu-plan.md`, D4).
    ///
    /// `void SDL_SetGPUScissor(void*, void*)`
    public static final class SetGPUScissor {

        private static final MethodHandle FD_SDL_SetGPUScissor =
                Downcalls.link(FunctionDescriptor.ofVoid(ADDRESS, ADDRESS));

        private final MemorySegment address;

        SetGPUScissor(SymbolLookup lookup) {
            this.address = Downcalls.symbol(lookup, "SDL_SetGPUScissor");
        }

        /// Calls `SDL_SetGPUScissor`.
        ///
        /// @param renderPass the pass
        /// @param scissor    an `SDL_Rect*`
        public void call(MemorySegment renderPass, MemorySegment scissor) {
            try {
                FD_SDL_SetGPUScissor.invokeExact(address, renderPass, scissor);
            } catch (Throwable t) {
                throw Downcalls.failure("SDL_SetGPUScissor", t);
            }
        }
    }

    /// Binds textures, each with its sampler, to the fragment shader's slots.
    ///
    /// `void SDL_BindGPUFragmentSamplers(void*, uint32_t, void*, uint32_t)`
    public static final class BindGPUFragmentSamplers {

        private static final MethodHandle FD_SDL_BindGPUFragmentSamplers =
                Downcalls.link(FunctionDescriptor.ofVoid(ADDRESS, JAVA_INT, ADDRESS, JAVA_INT));

        private final MemorySegment address;

        BindGPUFragmentSamplers(SymbolLookup lookup) {
            this.address = Downcalls.symbol(lookup, "SDL_BindGPUFragmentSamplers");
        }

        /// Calls `SDL_BindGPUFragmentSamplers`.
        ///
        /// @param renderPass the pass
        /// @param firstSlot  the first slot bound
        /// @param bindings   an `SDL_GPUTextureSamplerBinding*` array
        /// @param count      how many bindings
        public void call(MemorySegment renderPass, int firstSlot, MemorySegment bindings, int count) {
            try {
                FD_SDL_BindGPUFragmentSamplers.invokeExact(address, renderPass, firstSlot, bindings, count);
            } catch (Throwable t) {
                throw Downcalls.failure("SDL_BindGPUFragmentSamplers", t);
            }
        }
    }

    /// Draws, with the vertices made by the vertex shader from their ids.
    ///
    /// `void SDL_DrawGPUPrimitives(void*, uint32_t, uint32_t, uint32_t, uint32_t)`
    public static final class DrawGPUPrimitives {

        private static final MethodHandle FD_SDL_DrawGPUPrimitives =
                Downcalls.link(FunctionDescriptor.ofVoid(ADDRESS, JAVA_INT, JAVA_INT, JAVA_INT, JAVA_INT));

        private final MemorySegment address;

        DrawGPUPrimitives(SymbolLookup lookup) {
            this.address = Downcalls.symbol(lookup, "SDL_DrawGPUPrimitives");
        }

        /// Calls `SDL_DrawGPUPrimitives`.
        ///
        /// @param renderPass    the pass
        /// @param vertices      how many vertices
        /// @param instances     how many instances
        /// @param firstVertex   the first vertex id
        /// @param firstInstance the first instance id
        public void call(MemorySegment renderPass, int vertices, int instances, int firstVertex, int firstInstance) {
            try {
                FD_SDL_DrawGPUPrimitives.invokeExact(
                        address, renderPass, vertices, instances, firstVertex, firstInstance);
            } catch (Throwable t) {
                throw Downcalls.failure("SDL_DrawGPUPrimitives", t);
            }
        }
    }

    /// Sets a vertex-shader uniform block for the draws that follow. SDL copies it.
    ///
    /// `void SDL_PushGPUVertexUniformData(void*, uint32_t, void*, uint32_t)`
    public static final class PushGPUVertexUniformData {

        private static final MethodHandle FD_SDL_PushGPUVertexUniformData =
                Downcalls.link(FunctionDescriptor.ofVoid(ADDRESS, JAVA_INT, ADDRESS, JAVA_INT));

        private final MemorySegment address;

        PushGPUVertexUniformData(SymbolLookup lookup) {
            this.address = Downcalls.symbol(lookup, "SDL_PushGPUVertexUniformData");
        }

        /// Calls `SDL_PushGPUVertexUniformData`.
        ///
        /// @param commandBuffer the command buffer
        /// @param slot          the uniform slot
        /// @param data          the bytes, copied
        /// @param length        how many
        public void call(MemorySegment commandBuffer, int slot, MemorySegment data, int length) {
            try {
                FD_SDL_PushGPUVertexUniformData.invokeExact(address, commandBuffer, slot, data, length);
            } catch (Throwable t) {
                throw Downcalls.failure("SDL_PushGPUVertexUniformData", t);
            }
        }
    }

    /// Sets a fragment-shader uniform block for the draws that follow. SDL copies\nit.
    ///
    /// `void SDL_PushGPUFragmentUniformData(void*, uint32_t, void*, uint32_t)`
    public static final class PushGPUFragmentUniformData {

        private static final MethodHandle FD_SDL_PushGPUFragmentUniformData =
                Downcalls.link(FunctionDescriptor.ofVoid(ADDRESS, JAVA_INT, ADDRESS, JAVA_INT));

        private final MemorySegment address;

        PushGPUFragmentUniformData(SymbolLookup lookup) {
            this.address = Downcalls.symbol(lookup, "SDL_PushGPUFragmentUniformData");
        }

        /// Calls `SDL_PushGPUFragmentUniformData`.
        ///
        /// @param commandBuffer the command buffer
        /// @param slot          the uniform slot
        /// @param data          the bytes, copied
        /// @param length        how many
        public void call(MemorySegment commandBuffer, int slot, MemorySegment data, int length) {
            try {
                FD_SDL_PushGPUFragmentUniformData.invokeExact(address, commandBuffer, slot, data, length);
            } catch (Throwable t) {
                throw Downcalls.failure("SDL_PushGPUFragmentUniformData", t);
            }
        }
    }
}
