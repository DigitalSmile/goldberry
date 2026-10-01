package dev.goldberry.natives.sdl.calls;

import static java.lang.foreign.ValueLayout.ADDRESS;

import java.lang.foreign.FunctionDescriptor;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.SymbolLookup;
import java.lang.invoke.MethodHandle;

import dev.goldberry.natives.Downcalls;

/// What a draw is made with: shaders, samplers and graphics pipelines
/// (`docs/gpu-plan.md`, phase 2).
///
/// One holder per function: its handle, its address, and a `call` whose
/// parameters are the C prototype’s. See [Downcalls] for why the handle is a
/// `static final` constant and why these live in a package of their own.
public record SdlGpuPipelineCalls(
        CreateGPUShader createGPUShader,
        ReleaseGPUShader releaseGPUShader,
        CreateGPUSampler createGPUSampler,
        ReleaseGPUSampler releaseGPUSampler,
        CreateGPUGraphicsPipeline createGPUGraphicsPipeline,
        ReleaseGPUGraphicsPipeline releaseGPUGraphicsPipeline) {

    /// Binds every function above.
    ///
    /// @param lookup the loaded `libgoldberry`
    public static SdlGpuPipelineCalls bind(SymbolLookup lookup) {
        return new SdlGpuPipelineCalls(
                new CreateGPUShader(lookup),
                new ReleaseGPUShader(lookup),
                new CreateGPUSampler(lookup),
                new ReleaseGPUSampler(lookup),
                new CreateGPUGraphicsPipeline(lookup),
                new ReleaseGPUGraphicsPipeline(lookup));
    }

    /// `SDL_GPU_SHADERSTAGE_VERTEX`.
    public static final int SHADERSTAGE_VERTEX = 0;

    /// `SDL_GPU_SHADERSTAGE_FRAGMENT`.
    public static final int SHADERSTAGE_FRAGMENT = 1;

    // The address modes, primitive types, cull modes and front faces are enums
    // in `sdl.gpu` now, each value beside its name for the layout probe.

    /// `SDL_GPU_SAMPLERMIPMAPMODE_NEAREST`: textures here have one level.
    public static final int SAMPLERMIPMAPMODE_NEAREST = 0;

    /// `SDL_GPU_FILLMODE_FILL`.
    public static final int FILLMODE_FILL = 0;

    /// `SDL_GPU_BLENDFACTOR_ZERO`.
    public static final int BLENDFACTOR_ZERO = 1;

    /// `SDL_GPU_BLENDFACTOR_ONE`.
    public static final int BLENDFACTOR_ONE = 2;

    /// `SDL_GPU_BLENDFACTOR_ONE_MINUS_SRC_ALPHA`: premultiplied "over".
    public static final int BLENDFACTOR_ONE_MINUS_SRC_ALPHA = 8;

    /// `SDL_GPU_BLENDOP_ADD`.
    public static final int BLENDOP_ADD = 1;

    /// `SDL_GPU_COLORCOMPONENT_R | G | B | A`: every channel written.
    public static final int COLORCOMPONENT_RGBA = 0xF;

    /// Creates a shader from bytecode in one of the device's formats.
    ///
    /// `void* SDL_CreateGPUShader(void*, void*)`
    public static final class CreateGPUShader {

        private static final MethodHandle FD_SDL_CreateGPUShader =
                Downcalls.link(FunctionDescriptor.of(ADDRESS, ADDRESS, ADDRESS));

        private final MemorySegment address;

        CreateGPUShader(SymbolLookup lookup) {
            this.address = Downcalls.symbol(lookup, "SDL_CreateGPUShader");
        }

        /// Calls `SDL_CreateGPUShader`.
        ///
        /// @param device     the device
        /// @param createInfo an `SDL_GPUShaderCreateInfo*`
        /// @return an `SDL_GPUShader*`, or NULL on failure
        public MemorySegment call(MemorySegment device, MemorySegment createInfo) {
            try {
                return (MemorySegment) FD_SDL_CreateGPUShader.invokeExact(address, device, createInfo);
            } catch (Throwable t) {
                throw Downcalls.failure("SDL_CreateGPUShader", t);
            }
        }
    }

    /// Releases a shader. A pipeline made from it keeps working.
    ///
    /// `void SDL_ReleaseGPUShader(void*, void*)`
    public static final class ReleaseGPUShader {

        private static final MethodHandle FD_SDL_ReleaseGPUShader =
                Downcalls.link(FunctionDescriptor.ofVoid(ADDRESS, ADDRESS));

        private final MemorySegment address;

        ReleaseGPUShader(SymbolLookup lookup) {
            this.address = Downcalls.symbol(lookup, "SDL_ReleaseGPUShader");
        }

        /// Calls `SDL_ReleaseGPUShader`.
        ///
        /// @param device the device
        /// @param shader the shader
        public void call(MemorySegment device, MemorySegment shader) {
            try {
                FD_SDL_ReleaseGPUShader.invokeExact(address, device, shader);
            } catch (Throwable t) {
                throw Downcalls.failure("SDL_ReleaseGPUShader", t);
            }
        }
    }

    /// Creates a sampler: how a shader reads a texture.
    ///
    /// `void* SDL_CreateGPUSampler(void*, void*)`
    public static final class CreateGPUSampler {

        private static final MethodHandle FD_SDL_CreateGPUSampler =
                Downcalls.link(FunctionDescriptor.of(ADDRESS, ADDRESS, ADDRESS));

        private final MemorySegment address;

        CreateGPUSampler(SymbolLookup lookup) {
            this.address = Downcalls.symbol(lookup, "SDL_CreateGPUSampler");
        }

        /// Calls `SDL_CreateGPUSampler`.
        ///
        /// @param device     the device
        /// @param createInfo an `SDL_GPUSamplerCreateInfo*`
        /// @return an `SDL_GPUSampler*`, or NULL on failure
        public MemorySegment call(MemorySegment device, MemorySegment createInfo) {
            try {
                return (MemorySegment) FD_SDL_CreateGPUSampler.invokeExact(address, device, createInfo);
            } catch (Throwable t) {
                throw Downcalls.failure("SDL_CreateGPUSampler", t);
            }
        }
    }

    /// Releases a sampler.
    ///
    /// `void SDL_ReleaseGPUSampler(void*, void*)`
    public static final class ReleaseGPUSampler {

        private static final MethodHandle FD_SDL_ReleaseGPUSampler =
                Downcalls.link(FunctionDescriptor.ofVoid(ADDRESS, ADDRESS));

        private final MemorySegment address;

        ReleaseGPUSampler(SymbolLookup lookup) {
            this.address = Downcalls.symbol(lookup, "SDL_ReleaseGPUSampler");
        }

        /// Calls `SDL_ReleaseGPUSampler`.
        ///
        /// @param device  the device
        /// @param sampler the sampler
        public void call(MemorySegment device, MemorySegment sampler) {
            try {
                FD_SDL_ReleaseGPUSampler.invokeExact(address, device, sampler);
            } catch (Throwable t) {
                throw Downcalls.failure("SDL_ReleaseGPUSampler", t);
            }
        }
    }

    /// Creates a graphics pipeline: two shaders, how vertices arrive, how they are\nrasterised, and the target format
    /// and blending they draw with.
    ///
    /// `void* SDL_CreateGPUGraphicsPipeline(void*, void*)`
    public static final class CreateGPUGraphicsPipeline {

        private static final MethodHandle FD_SDL_CreateGPUGraphicsPipeline =
                Downcalls.link(FunctionDescriptor.of(ADDRESS, ADDRESS, ADDRESS));

        private final MemorySegment address;

        CreateGPUGraphicsPipeline(SymbolLookup lookup) {
            this.address = Downcalls.symbol(lookup, "SDL_CreateGPUGraphicsPipeline");
        }

        /// Calls `SDL_CreateGPUGraphicsPipeline`.
        ///
        /// @param device     the device
        /// @param createInfo an `SDL_GPUGraphicsPipelineCreateInfo*`
        /// @return an `SDL_GPUGraphicsPipeline*`, or NULL on failure
        public MemorySegment call(MemorySegment device, MemorySegment createInfo) {
            try {
                return (MemorySegment) FD_SDL_CreateGPUGraphicsPipeline.invokeExact(address, device, createInfo);
            } catch (Throwable t) {
                throw Downcalls.failure("SDL_CreateGPUGraphicsPipeline", t);
            }
        }
    }

    /// Releases a graphics pipeline.
    ///
    /// `void SDL_ReleaseGPUGraphicsPipeline(void*, void*)`
    public static final class ReleaseGPUGraphicsPipeline {

        private static final MethodHandle FD_SDL_ReleaseGPUGraphicsPipeline =
                Downcalls.link(FunctionDescriptor.ofVoid(ADDRESS, ADDRESS));

        private final MemorySegment address;

        ReleaseGPUGraphicsPipeline(SymbolLookup lookup) {
            this.address = Downcalls.symbol(lookup, "SDL_ReleaseGPUGraphicsPipeline");
        }

        /// Calls `SDL_ReleaseGPUGraphicsPipeline`.
        ///
        /// @param device   the device
        /// @param pipeline the pipeline
        public void call(MemorySegment device, MemorySegment pipeline) {
            try {
                FD_SDL_ReleaseGPUGraphicsPipeline.invokeExact(address, device, pipeline);
            } catch (Throwable t) {
                throw Downcalls.failure("SDL_ReleaseGPUGraphicsPipeline", t);
            }
        }
    }
}
