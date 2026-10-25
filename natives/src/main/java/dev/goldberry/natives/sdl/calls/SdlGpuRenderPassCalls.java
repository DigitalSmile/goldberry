package dev.goldberry.natives.sdl.calls;

import static java.lang.foreign.ValueLayout.ADDRESS;
import static java.lang.foreign.ValueLayout.JAVA_BYTE;
import static java.lang.foreign.ValueLayout.JAVA_FLOAT;
import static java.lang.foreign.ValueLayout.JAVA_INT;

import java.lang.foreign.FunctionDescriptor;
import java.lang.foreign.MemoryLayout;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.SymbolLookup;
import java.lang.invoke.MethodHandle;

import dev.goldberry.natives.Downcalls;

/// What a render pass records: the pipeline, the viewport and scissor, the
/// textures sampled, the uniforms pushed, the stencil reference and blend
/// constants, and the draws, direct and indirect.
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
        PushGPUFragmentUniformData pushGPUFragmentUniformData,
        BindGPUVertexSamplers bindGPUVertexSamplers,
        BindGPUVertexStorageBuffers bindGPUVertexStorageBuffers,
        BindGPUFragmentStorageBuffers bindGPUFragmentStorageBuffers,
        BindGPUFragmentStorageTextures bindGPUFragmentStorageTextures,
        BindGPUVertexStorageTextures bindGPUVertexStorageTextures,
        SetGPUStencilReference setGPUStencilReference,
        SetGPUBlendConstants setGPUBlendConstants,
        DrawGPUPrimitivesIndirect drawGPUPrimitivesIndirect,
        DrawGPUIndexedPrimitivesIndirect drawGPUIndexedPrimitivesIndirect) {

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
                new PushGPUFragmentUniformData(lookup),
                new BindGPUVertexSamplers(lookup),
                new BindGPUVertexStorageBuffers(lookup),
                new BindGPUFragmentStorageBuffers(lookup),
                new BindGPUFragmentStorageTextures(lookup),
                new BindGPUVertexStorageTextures(lookup),
                new SetGPUStencilReference(lookup),
                new SetGPUBlendConstants(lookup),
                new DrawGPUPrimitivesIndirect(lookup),
                new DrawGPUIndexedPrimitivesIndirect(lookup));
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

    /// Sets the rectangle outside which the next draws write nothing: a GPU layer's
    /// clip.
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

    /// Sets a fragment-shader uniform block for the draws that follow. SDL copies
    /// it.
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

    /// Binds textures and samplers the vertex shader reads: a height map, an instance table.
    ///
    /// `void SDL_BindGPUVertexSamplers(void*, Uint32, void*, Uint32)`
    public static final class BindGPUVertexSamplers {

        private static final MethodHandle FD_SDL_BindGPUVertexSamplers =
                Downcalls.link(FunctionDescriptor.ofVoid(ADDRESS, JAVA_INT, ADDRESS, JAVA_INT));

        private final MemorySegment address;

        BindGPUVertexSamplers(SymbolLookup lookup) {
            this.address = Downcalls.symbol(lookup, "SDL_BindGPUVertexSamplers");
        }

        /// Calls `SDL_BindGPUVertexSamplers`.
        ///
        /// @param renderPass the pass
        /// @param firstSlot  the first slot bound
        /// @param bindings   an `SDL_GPUTextureSamplerBinding*` array
        /// @param count      how many bindings
        public void call(MemorySegment renderPass, int firstSlot, MemorySegment bindings, int count) {
            try {
                FD_SDL_BindGPUVertexSamplers.invokeExact(address, renderPass, firstSlot, bindings, count);
            } catch (Throwable t) {
                throw Downcalls.failure("SDL_BindGPUVertexSamplers", t);
            }
        }
    }

    /// Binds storage buffers the vertex shader reads.
    ///
    /// `void SDL_BindGPUVertexStorageBuffers(void*, Uint32, void*, Uint32)`
    public static final class BindGPUVertexStorageBuffers {

        private static final MethodHandle FD_SDL_BindGPUVertexStorageBuffers =
                Downcalls.link(FunctionDescriptor.ofVoid(ADDRESS, JAVA_INT, ADDRESS, JAVA_INT));

        private final MemorySegment address;

        BindGPUVertexStorageBuffers(SymbolLookup lookup) {
            this.address = Downcalls.symbol(lookup, "SDL_BindGPUVertexStorageBuffers");
        }

        /// Calls `SDL_BindGPUVertexStorageBuffers`.
        ///
        /// @param renderPass the pass
        /// @param firstSlot  the first slot bound
        /// @param bindings   an `SDL_GPUBuffer**` array
        /// @param count      how many bindings
        public void call(MemorySegment renderPass, int firstSlot, MemorySegment bindings, int count) {
            try {
                FD_SDL_BindGPUVertexStorageBuffers.invokeExact(address, renderPass, firstSlot, bindings, count);
            } catch (Throwable t) {
                throw Downcalls.failure("SDL_BindGPUVertexStorageBuffers", t);
            }
        }
    }

    /// Binds storage buffers the fragment shader reads.
    ///
    /// `void SDL_BindGPUFragmentStorageBuffers(void*, Uint32, void*, Uint32)`
    public static final class BindGPUFragmentStorageBuffers {

        private static final MethodHandle FD_SDL_BindGPUFragmentStorageBuffers =
                Downcalls.link(FunctionDescriptor.ofVoid(ADDRESS, JAVA_INT, ADDRESS, JAVA_INT));

        private final MemorySegment address;

        BindGPUFragmentStorageBuffers(SymbolLookup lookup) {
            this.address = Downcalls.symbol(lookup, "SDL_BindGPUFragmentStorageBuffers");
        }

        /// Calls `SDL_BindGPUFragmentStorageBuffers`.
        ///
        /// @param renderPass the pass
        /// @param firstSlot  the first slot bound
        /// @param bindings   an `SDL_GPUBuffer**` array
        /// @param count      how many bindings
        public void call(MemorySegment renderPass, int firstSlot, MemorySegment bindings, int count) {
            try {
                FD_SDL_BindGPUFragmentStorageBuffers.invokeExact(address, renderPass, firstSlot, bindings, count);
            } catch (Throwable t) {
                throw Downcalls.failure("SDL_BindGPUFragmentStorageBuffers", t);
            }
        }
    }

    /// Binds storage textures the fragment shader reads.
    ///
    /// `void SDL_BindGPUFragmentStorageTextures(void*, Uint32, void*, Uint32)`
    public static final class BindGPUFragmentStorageTextures {

        private static final MethodHandle FD_SDL_BindGPUFragmentStorageTextures =
                Downcalls.link(FunctionDescriptor.ofVoid(ADDRESS, JAVA_INT, ADDRESS, JAVA_INT));

        private final MemorySegment address;

        BindGPUFragmentStorageTextures(SymbolLookup lookup) {
            this.address = Downcalls.symbol(lookup, "SDL_BindGPUFragmentStorageTextures");
        }

        /// Calls `SDL_BindGPUFragmentStorageTextures`.
        ///
        /// @param renderPass the pass
        /// @param firstSlot  the first slot bound
        /// @param bindings   an `SDL_GPUTexture**` array
        /// @param count      how many bindings
        public void call(MemorySegment renderPass, int firstSlot, MemorySegment bindings, int count) {
            try {
                FD_SDL_BindGPUFragmentStorageTextures.invokeExact(address, renderPass, firstSlot, bindings, count);
            } catch (Throwable t) {
                throw Downcalls.failure("SDL_BindGPUFragmentStorageTextures", t);
            }
        }
    }

    /// Binds storage textures the vertex shader reads.
    ///
    /// `void SDL_BindGPUVertexStorageTextures(void*, Uint32, void*, Uint32)`
    public static final class BindGPUVertexStorageTextures {

        private static final MethodHandle FD_SDL_BindGPUVertexStorageTextures =
                Downcalls.link(FunctionDescriptor.ofVoid(ADDRESS, JAVA_INT, ADDRESS, JAVA_INT));

        private final MemorySegment address;

        BindGPUVertexStorageTextures(SymbolLookup lookup) {
            this.address = Downcalls.symbol(lookup, "SDL_BindGPUVertexStorageTextures");
        }

        /// Calls `SDL_BindGPUVertexStorageTextures`.
        ///
        /// @param renderPass the pass
        /// @param firstSlot  the first slot bound
        /// @param bindings   an `SDL_GPUTexture**` array
        /// @param count      how many bindings
        public void call(MemorySegment renderPass, int firstSlot, MemorySegment bindings, int count) {
            try {
                FD_SDL_BindGPUVertexStorageTextures.invokeExact(address, renderPass, firstSlot, bindings, count);
            } catch (Throwable t) {
                throw Downcalls.failure("SDL_BindGPUVertexStorageTextures", t);
            }
        }
    }

    /// Sets the value the stencil test of the next draws compares against and
    /// writes with `REPLACE`.
    ///
    /// `void SDL_SetGPUStencilReference(void*, Uint8)`
    public static final class SetGPUStencilReference {

        private static final MethodHandle FD_SDL_SetGPUStencilReference =
                Downcalls.link(FunctionDescriptor.ofVoid(ADDRESS, JAVA_BYTE));

        private final MemorySegment address;

        SetGPUStencilReference(SymbolLookup lookup) {
            this.address = Downcalls.symbol(lookup, "SDL_SetGPUStencilReference");
        }

        /// Calls `SDL_SetGPUStencilReference`.
        ///
        /// @param renderPass the pass
        /// @param reference  the reference, an unsigned byte
        public void call(MemorySegment renderPass, byte reference) {
            try {
                FD_SDL_SetGPUStencilReference.invokeExact(address, renderPass, reference);
            } catch (Throwable t) {
                throw Downcalls.failure("SDL_SetGPUStencilReference", t);
            }
        }
    }

    /// Sets the colour the constant blend factors of the next draws read.
    ///
    /// `void SDL_SetGPUBlendConstants(void*, SDL_FColor)`: the colour is passed
    /// by value, as a struct of four floats.
    public static final class SetGPUBlendConstants {

        /// `SDL_FColor`, written out rather than taken from `Layouts`: this
        /// package is initialised when a native image is built, and nothing
        /// else should be. The layout probe checks `SDL_FColor` itself.
        private static final MemoryLayout FCOLOR = MemoryLayout.structLayout(
                JAVA_FLOAT.withName("r"), JAVA_FLOAT.withName("g"), JAVA_FLOAT.withName("b"), JAVA_FLOAT.withName("a"));

        private static final MethodHandle FD_SDL_SetGPUBlendConstants =
                Downcalls.link(FunctionDescriptor.ofVoid(ADDRESS, FCOLOR));

        private final MemorySegment address;

        SetGPUBlendConstants(SymbolLookup lookup) {
            this.address = Downcalls.symbol(lookup, "SDL_SetGPUBlendConstants");
        }

        /// Calls `SDL_SetGPUBlendConstants`.
        ///
        /// @param renderPass the pass
        /// @param color      an `SDL_FColor`, copied
        public void call(MemorySegment renderPass, MemorySegment color) {
            try {
                FD_SDL_SetGPUBlendConstants.invokeExact(address, renderPass, color);
            } catch (Throwable t) {
                throw Downcalls.failure("SDL_SetGPUBlendConstants", t);
            }
        }
    }

    /// Draws as many times as `SDL_GPUIndirectDrawCommand`s a buffer holds,
    /// one after the other from an offset: draws whose counts the GPU wrote.
    ///
    /// `void SDL_DrawGPUPrimitivesIndirect(void*, void*, Uint32, Uint32)`
    public static final class DrawGPUPrimitivesIndirect {

        private static final MethodHandle FD_SDL_DrawGPUPrimitivesIndirect =
                Downcalls.link(FunctionDescriptor.ofVoid(ADDRESS, ADDRESS, JAVA_INT, JAVA_INT));

        private final MemorySegment address;

        DrawGPUPrimitivesIndirect(SymbolLookup lookup) {
            this.address = Downcalls.symbol(lookup, "SDL_DrawGPUPrimitivesIndirect");
        }

        /// Calls `SDL_DrawGPUPrimitivesIndirect`.
        ///
        /// @param renderPass the pass
        /// @param buffer     an `SDL_GPUBuffer*` made for indirect use
        /// @param offset     the byte the first command starts at
        /// @param drawCount  how many commands
        public void call(MemorySegment renderPass, MemorySegment buffer, int offset, int drawCount) {
            try {
                FD_SDL_DrawGPUPrimitivesIndirect.invokeExact(address, renderPass, buffer, offset, drawCount);
            } catch (Throwable t) {
                throw Downcalls.failure("SDL_DrawGPUPrimitivesIndirect", t);
            }
        }
    }

    /// Draws as many times as `SDL_GPUIndexedIndirectDrawCommand`s a buffer
    /// holds, from the bound index buffer.
    ///
    /// `void SDL_DrawGPUIndexedPrimitivesIndirect(void*, void*, Uint32, Uint32)`
    public static final class DrawGPUIndexedPrimitivesIndirect {

        private static final MethodHandle FD_SDL_DrawGPUIndexedPrimitivesIndirect =
                Downcalls.link(FunctionDescriptor.ofVoid(ADDRESS, ADDRESS, JAVA_INT, JAVA_INT));

        private final MemorySegment address;

        DrawGPUIndexedPrimitivesIndirect(SymbolLookup lookup) {
            this.address = Downcalls.symbol(lookup, "SDL_DrawGPUIndexedPrimitivesIndirect");
        }

        /// Calls `SDL_DrawGPUIndexedPrimitivesIndirect`.
        ///
        /// @param renderPass the pass
        /// @param buffer     an `SDL_GPUBuffer*` made for indirect use
        /// @param offset     the byte the first command starts at
        /// @param drawCount  how many commands
        public void call(MemorySegment renderPass, MemorySegment buffer, int offset, int drawCount) {
            try {
                FD_SDL_DrawGPUIndexedPrimitivesIndirect.invokeExact(address, renderPass, buffer, offset, drawCount);
            } catch (Throwable t) {
                throw Downcalls.failure("SDL_DrawGPUIndexedPrimitivesIndirect", t);
            }
        }
    }
}
