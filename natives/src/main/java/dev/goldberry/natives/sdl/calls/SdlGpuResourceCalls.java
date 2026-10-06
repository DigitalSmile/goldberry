package dev.goldberry.natives.sdl.calls;

import static java.lang.foreign.ValueLayout.ADDRESS;
import static java.lang.foreign.ValueLayout.JAVA_BOOLEAN;

import java.lang.foreign.FunctionDescriptor;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.SymbolLookup;
import java.lang.invoke.MethodHandle;

import dev.goldberry.natives.Downcalls;

/// What a GPU device holds: textures, and the transfer buffers pixels travel
/// through on their way to or from one.
///
/// One holder per function: its handle, its address, and a `call` whose
/// parameters are the C prototype’s. See [Downcalls] for why the handle is a
/// `static final` constant and why these live in a package of their own.
public record SdlGpuResourceCalls(
        CreateGPUTexture createGPUTexture,
        ReleaseGPUTexture releaseGPUTexture,
        CreateGPUTransferBuffer createGPUTransferBuffer,
        ReleaseGPUTransferBuffer releaseGPUTransferBuffer,
        MapGPUTransferBuffer mapGPUTransferBuffer,
        UnmapGPUTransferBuffer unmapGPUTransferBuffer) {

    /// Binds every function above.
    ///
    /// @param lookup the loaded `libgoldberry`
    public static SdlGpuResourceCalls bind(SymbolLookup lookup) {
        return new SdlGpuResourceCalls(
                new CreateGPUTexture(lookup),
                new ReleaseGPUTexture(lookup),
                new CreateGPUTransferBuffer(lookup),
                new ReleaseGPUTransferBuffer(lookup),
                new MapGPUTransferBuffer(lookup),
                new UnmapGPUTransferBuffer(lookup));
    }

    /// `SDL_GPU_TEXTURETYPE_2D`: a texture with one layer.
    public static final int TEXTURETYPE_2D = 0;

    /// `SDL_GPU_TEXTURETYPE_2D_ARRAY`: a texture with more than one layer.
    public static final int TEXTURETYPE_2D_ARRAY = 1;

    /// Creates a texture.
    ///
    /// `void* SDL_CreateGPUTexture(void*, void*)`
    public static final class CreateGPUTexture {

        private static final MethodHandle FD_SDL_CreateGPUTexture =
                Downcalls.link(FunctionDescriptor.of(ADDRESS, ADDRESS, ADDRESS));

        private final MemorySegment address;

        CreateGPUTexture(SymbolLookup lookup) {
            this.address = Downcalls.symbol(lookup, "SDL_CreateGPUTexture");
        }

        /// Calls `SDL_CreateGPUTexture`.
        ///
        /// @param device     the device
        /// @param createInfo an `SDL_GPUTextureCreateInfo*`
        /// @return an `SDL_GPUTexture*`, or NULL on failure
        public MemorySegment call(MemorySegment device, MemorySegment createInfo) {
            try {
                return (MemorySegment) FD_SDL_CreateGPUTexture.invokeExact(address, device, createInfo);
            } catch (Throwable t) {
                throw Downcalls.failure("SDL_CreateGPUTexture", t);
            }
        }
    }

    /// Releases a texture. SDL frees it once no submitted command still uses it.
    ///
    /// `void SDL_ReleaseGPUTexture(void*, void*)`
    public static final class ReleaseGPUTexture {

        private static final MethodHandle FD_SDL_ReleaseGPUTexture =
                Downcalls.link(FunctionDescriptor.ofVoid(ADDRESS, ADDRESS));

        private final MemorySegment address;

        ReleaseGPUTexture(SymbolLookup lookup) {
            this.address = Downcalls.symbol(lookup, "SDL_ReleaseGPUTexture");
        }

        /// Calls `SDL_ReleaseGPUTexture`.
        ///
        /// @param device  the device
        /// @param texture the texture
        public void call(MemorySegment device, MemorySegment texture) {
            try {
                FD_SDL_ReleaseGPUTexture.invokeExact(address, device, texture);
            } catch (Throwable t) {
                throw Downcalls.failure("SDL_ReleaseGPUTexture", t);
            }
        }
    }

    /// Creates a transfer buffer: memory the CPU maps, and a copy pass moves to or
    /// from a texture.
    ///
    /// `void* SDL_CreateGPUTransferBuffer(void*, void*)`
    public static final class CreateGPUTransferBuffer {

        private static final MethodHandle FD_SDL_CreateGPUTransferBuffer =
                Downcalls.link(FunctionDescriptor.of(ADDRESS, ADDRESS, ADDRESS));

        private final MemorySegment address;

        CreateGPUTransferBuffer(SymbolLookup lookup) {
            this.address = Downcalls.symbol(lookup, "SDL_CreateGPUTransferBuffer");
        }

        /// Calls `SDL_CreateGPUTransferBuffer`.
        ///
        /// @param device     the device
        /// @param createInfo an `SDL_GPUTransferBufferCreateInfo*`
        /// @return an `SDL_GPUTransferBuffer*`, or NULL on failure
        public MemorySegment call(MemorySegment device, MemorySegment createInfo) {
            try {
                return (MemorySegment) FD_SDL_CreateGPUTransferBuffer.invokeExact(address, device, createInfo);
            } catch (Throwable t) {
                throw Downcalls.failure("SDL_CreateGPUTransferBuffer", t);
            }
        }
    }

    /// Releases a transfer buffer, once no submitted command still uses it.
    ///
    /// `void SDL_ReleaseGPUTransferBuffer(void*, void*)`
    public static final class ReleaseGPUTransferBuffer {

        private static final MethodHandle FD_SDL_ReleaseGPUTransferBuffer =
                Downcalls.link(FunctionDescriptor.ofVoid(ADDRESS, ADDRESS));

        private final MemorySegment address;

        ReleaseGPUTransferBuffer(SymbolLookup lookup) {
            this.address = Downcalls.symbol(lookup, "SDL_ReleaseGPUTransferBuffer");
        }

        /// Calls `SDL_ReleaseGPUTransferBuffer`.
        ///
        /// @param device         the device
        /// @param transferBuffer the buffer
        public void call(MemorySegment device, MemorySegment transferBuffer) {
            try {
                FD_SDL_ReleaseGPUTransferBuffer.invokeExact(address, device, transferBuffer);
            } catch (Throwable t) {
                throw Downcalls.failure("SDL_ReleaseGPUTransferBuffer", t);
            }
        }
    }

    /// Maps a transfer buffer into the CPU's address space.
    ///
    /// `void* SDL_MapGPUTransferBuffer(void*, void*, _Bool)`
    public static final class MapGPUTransferBuffer {

        private static final MethodHandle FD_SDL_MapGPUTransferBuffer =
                Downcalls.link(FunctionDescriptor.of(ADDRESS, ADDRESS, ADDRESS, JAVA_BOOLEAN));

        private final MemorySegment address;

        MapGPUTransferBuffer(SymbolLookup lookup) {
            this.address = Downcalls.symbol(lookup, "SDL_MapGPUTransferBuffer");
        }

        /// Calls `SDL_MapGPUTransferBuffer`.
        ///
        /// @param device         the device
        /// @param transferBuffer the buffer
        /// @param cycle          whether to take fresh memory if the GPU still reads the old
        /// @return the first byte, or NULL on failure
        public MemorySegment call(MemorySegment device, MemorySegment transferBuffer, boolean cycle) {
            try {
                return (MemorySegment) FD_SDL_MapGPUTransferBuffer.invokeExact(address, device, transferBuffer, cycle);
            } catch (Throwable t) {
                throw Downcalls.failure("SDL_MapGPUTransferBuffer", t);
            }
        }
    }

    /// Unmaps a transfer buffer. The pointer mapping it returned is invalid after.
    ///
    /// `void SDL_UnmapGPUTransferBuffer(void*, void*)`
    public static final class UnmapGPUTransferBuffer {

        private static final MethodHandle FD_SDL_UnmapGPUTransferBuffer =
                Downcalls.link(FunctionDescriptor.ofVoid(ADDRESS, ADDRESS));

        private final MemorySegment address;

        UnmapGPUTransferBuffer(SymbolLookup lookup) {
            this.address = Downcalls.symbol(lookup, "SDL_UnmapGPUTransferBuffer");
        }

        /// Calls `SDL_UnmapGPUTransferBuffer`.
        ///
        /// @param device         the device
        /// @param transferBuffer the buffer
        public void call(MemorySegment device, MemorySegment transferBuffer) {
            try {
                FD_SDL_UnmapGPUTransferBuffer.invokeExact(address, device, transferBuffer);
            } catch (Throwable t) {
                throw Downcalls.failure("SDL_UnmapGPUTransferBuffer", t);
            }
        }
    }
}
