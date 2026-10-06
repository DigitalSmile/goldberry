package dev.goldberry.natives.sdl.calls;

import static java.lang.foreign.ValueLayout.ADDRESS;
import static java.lang.foreign.ValueLayout.JAVA_BOOLEAN;
import static java.lang.foreign.ValueLayout.JAVA_INT;

import java.lang.foreign.FunctionDescriptor;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.SymbolLookup;
import java.lang.invoke.MethodHandle;

import dev.goldberry.natives.Downcalls;

/// SDL's GPU device: the one context `canvas3d`, the composited window and GPU
/// video present share. There is one per process, made the first time something
/// needs it and never at start-up.
///
/// One holder per function: its handle, its address, and a `call` whose
/// parameters are the C prototype’s. See [Downcalls] for why the handle is a
/// `static final` constant and why these live in a package of their own.
///
/// Read more: [The GPU canvas](https://goldberry.dev/docs/components/gpu.html#what-the-module-does-to-a-window) and
/// [The native boundary](https://goldberry.dev/docs/overview/architecture.html#the-native-boundary).
public record SdlGpuDeviceCalls(
        CreateGPUDeviceWithProperties createGPUDeviceWithProperties,
        DestroyGPUDevice destroyGPUDevice,
        GetNumGPUDrivers getNumGPUDrivers,
        GetGPUDriver getGPUDriver,
        GetGPUDeviceDriver getGPUDeviceDriver,
        GetGPUShaderFormats getGPUShaderFormats,
        TextureSupportsFormat textureSupportsFormat,
        TextureSupportsSampleCount textureSupportsSampleCount) {

    /// Binds every function above.
    ///
    /// @param lookup the loaded `libgoldberry`
    public static SdlGpuDeviceCalls bind(SymbolLookup lookup) {
        return new SdlGpuDeviceCalls(
                new CreateGPUDeviceWithProperties(lookup),
                new DestroyGPUDevice(lookup),
                new GetNumGPUDrivers(lookup),
                new GetGPUDriver(lookup),
                new GetGPUDeviceDriver(lookup),
                new GetGPUShaderFormats(lookup),
                new TextureSupportsFormat(lookup),
                new TextureSupportsSampleCount(lookup));
    }

    /// Creates a GPU device on the first driver that can serve the options.
    ///
    /// Needs the video subsystem, and a video driver that can make a Metal view or
    /// a Vulkan surface: SDL's `dummy` driver can do neither, so there is no device
    /// under it, while `offscreen` has headless Vulkan and does.
    ///
    /// `void* SDL_CreateGPUDeviceWithProperties(uint32_t)`
    public static final class CreateGPUDeviceWithProperties {

        private static final MethodHandle FD_SDL_CreateGPUDeviceWithProperties =
                Downcalls.link(FunctionDescriptor.of(ADDRESS, JAVA_INT));

        private final MemorySegment address;

        CreateGPUDeviceWithProperties(SymbolLookup lookup) {
            this.address = Downcalls.symbol(lookup, "SDL_CreateGPUDeviceWithProperties");
        }

        /// Calls `SDL_CreateGPUDeviceWithProperties`.
        ///
        /// @param props the options, a property group
        /// @return an `SDL_GPUDevice*`, or NULL when no driver could be created
        public MemorySegment call(int props) {
            try {
                return (MemorySegment) FD_SDL_CreateGPUDeviceWithProperties.invokeExact(address, props);
            } catch (Throwable t) {
                throw Downcalls.failure("SDL_CreateGPUDeviceWithProperties", t);
            }
        }
    }

    /// Destroys a device. Everything created on it must have been released.
    ///
    /// `void SDL_DestroyGPUDevice(void*)`
    public static final class DestroyGPUDevice {

        private static final MethodHandle FD_SDL_DestroyGPUDevice = Downcalls.link(FunctionDescriptor.ofVoid(ADDRESS));

        private final MemorySegment address;

        DestroyGPUDevice(SymbolLookup lookup) {
            this.address = Downcalls.symbol(lookup, "SDL_DestroyGPUDevice");
        }

        /// Calls `SDL_DestroyGPUDevice`.
        ///
        /// @param device the device
        public void call(MemorySegment device) {
            try {
                FD_SDL_DestroyGPUDevice.invokeExact(address, device);
            } catch (Throwable t) {
                throw Downcalls.failure("SDL_DestroyGPUDevice", t);
            }
        }
    }

    /// How many GPU drivers this build of SDL has compiled in.
    ///
    /// `int SDL_GetNumGPUDrivers()`
    public static final class GetNumGPUDrivers {

        private static final MethodHandle FD_SDL_GetNumGPUDrivers = Downcalls.link(FunctionDescriptor.of(JAVA_INT));

        private final MemorySegment address;

        GetNumGPUDrivers(SymbolLookup lookup) {
            this.address = Downcalls.symbol(lookup, "SDL_GetNumGPUDrivers");
        }

        /// Calls `SDL_GetNumGPUDrivers`.
        ///
        /// @return the count
        public int call() {
            try {
                return (int) FD_SDL_GetNumGPUDrivers.invokeExact(address);
            } catch (Throwable t) {
                throw Downcalls.failure("SDL_GetNumGPUDrivers", t);
            }
        }
    }

    /// The name of a compiled-in GPU driver: `vulkan`, `metal`, `direct3d12`.
    ///
    /// `void* SDL_GetGPUDriver(int)`
    public static final class GetGPUDriver {

        private static final MethodHandle FD_SDL_GetGPUDriver =
                Downcalls.link(FunctionDescriptor.of(ADDRESS, JAVA_INT));

        private final MemorySegment address;

        GetGPUDriver(SymbolLookup lookup) {
            this.address = Downcalls.symbol(lookup, "SDL_GetGPUDriver");
        }

        /// Calls `SDL_GetGPUDriver`.
        ///
        /// @param index from 0 to the count, exclusive
        /// @return a C string SDL owns
        public MemorySegment call(int index) {
            try {
                return (MemorySegment) FD_SDL_GetGPUDriver.invokeExact(address, index);
            } catch (Throwable t) {
                throw Downcalls.failure("SDL_GetGPUDriver", t);
            }
        }
    }

    /// The name of the driver a device was created on.
    ///
    /// `void* SDL_GetGPUDeviceDriver(void*)`
    public static final class GetGPUDeviceDriver {

        private static final MethodHandle FD_SDL_GetGPUDeviceDriver =
                Downcalls.link(FunctionDescriptor.of(ADDRESS, ADDRESS));

        private final MemorySegment address;

        GetGPUDeviceDriver(SymbolLookup lookup) {
            this.address = Downcalls.symbol(lookup, "SDL_GetGPUDeviceDriver");
        }

        /// Calls `SDL_GetGPUDeviceDriver`.
        ///
        /// @param device the device
        /// @return a C string SDL owns
        public MemorySegment call(MemorySegment device) {
            try {
                return (MemorySegment) FD_SDL_GetGPUDeviceDriver.invokeExact(address, device);
            } catch (Throwable t) {
                throw Downcalls.failure("SDL_GetGPUDeviceDriver", t);
            }
        }
    }

    /// The shader formats a device accepts, as `SDL_GPU_SHADERFORMAT_*` bits.
    ///
    /// `uint32_t SDL_GetGPUShaderFormats(void*)`
    public static final class GetGPUShaderFormats {

        private static final MethodHandle FD_SDL_GetGPUShaderFormats =
                Downcalls.link(FunctionDescriptor.of(JAVA_INT, ADDRESS));

        private final MemorySegment address;

        GetGPUShaderFormats(SymbolLookup lookup) {
            this.address = Downcalls.symbol(lookup, "SDL_GetGPUShaderFormats");
        }

        /// Calls `SDL_GetGPUShaderFormats`.
        ///
        /// @param device the device
        /// @return the bits
        public int call(MemorySegment device) {
            try {
                return (int) FD_SDL_GetGPUShaderFormats.invokeExact(address, device);
            } catch (Throwable t) {
                throw Downcalls.failure("SDL_GetGPUShaderFormats", t);
            }
        }
    }

    /// Whether a device can make a texture of a format, type and usage.
    ///
    /// `_Bool SDL_GPUTextureSupportsFormat(void*, uint32_t, uint32_t, uint32_t)`
    public static final class TextureSupportsFormat {

        private static final MethodHandle FD_SDL_GPUTextureSupportsFormat =
                Downcalls.link(FunctionDescriptor.of(JAVA_BOOLEAN, ADDRESS, JAVA_INT, JAVA_INT, JAVA_INT));

        private final MemorySegment address;

        TextureSupportsFormat(SymbolLookup lookup) {
            this.address = Downcalls.symbol(lookup, "SDL_GPUTextureSupportsFormat");
        }

        /// Calls `SDL_GPUTextureSupportsFormat`.
        ///
        /// @param device the device
        /// @param format an `SDL_GPUTextureFormat`
        /// @param type   an `SDL_GPUTextureType`
        /// @param usage  `SDL_GPU_TEXTUREUSAGE_*` bits
        /// @return true if it can
        public boolean call(MemorySegment device, int format, int type, int usage) {
            try {
                return (boolean) FD_SDL_GPUTextureSupportsFormat.invokeExact(address, device, format, type, usage);
            } catch (Throwable t) {
                throw Downcalls.failure("SDL_GPUTextureSupportsFormat", t);
            }
        }
    }

    /// Whether a device can make a texture of a format multisampled at a
    /// count.
    ///
    /// `_Bool SDL_GPUTextureSupportsSampleCount(void*, uint32_t, uint32_t)`
    public static final class TextureSupportsSampleCount {

        private static final MethodHandle FD_SDL_GPUTextureSupportsSampleCount =
                Downcalls.link(FunctionDescriptor.of(JAVA_BOOLEAN, ADDRESS, JAVA_INT, JAVA_INT));

        private final MemorySegment address;

        TextureSupportsSampleCount(SymbolLookup lookup) {
            this.address = Downcalls.symbol(lookup, "SDL_GPUTextureSupportsSampleCount");
        }

        /// Calls `SDL_GPUTextureSupportsSampleCount`.
        ///
        /// @param device      the device
        /// @param format      an `SDL_GPUTextureFormat`
        /// @param sampleCount an `SDL_GPUSampleCount`
        /// @return true if it can
        public boolean call(MemorySegment device, int format, int sampleCount) {
            try {
                return (boolean) FD_SDL_GPUTextureSupportsSampleCount.invokeExact(address, device, format, sampleCount);
            } catch (Throwable t) {
                throw Downcalls.failure("SDL_GPUTextureSupportsSampleCount", t);
            }
        }
    }
}
