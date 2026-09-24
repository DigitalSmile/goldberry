package io.github.digitalsmile.goldberry.natives.sdl.calls;

import static java.lang.foreign.ValueLayout.ADDRESS;
import static java.lang.foreign.ValueLayout.JAVA_BOOLEAN;
import static java.lang.foreign.ValueLayout.JAVA_INT;

import java.lang.foreign.FunctionDescriptor;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.SymbolLookup;
import java.lang.invoke.MethodHandle;

import io.github.digitalsmile.goldberry.natives.Downcalls;

/// A window claimed by a GPU device: its swapchain, how it presents, and the
/// texture each frame renders into (`docs/gpu-plan.md`, D3).
///
/// One holder per function: its handle, its address, and a `call` whose
/// parameters are the C prototype’s. See [Downcalls] for why the handle is a
/// `static final` constant and why these live in a package of their own.
public record SdlGpuSwapchainCalls(
        ClaimWindowForGPUDevice claimWindowForGPUDevice,
        ReleaseWindowFromGPUDevice releaseWindowFromGPUDevice,
        SetGPUSwapchainParameters setGPUSwapchainParameters,
        WindowSupportsGPUPresentMode windowSupportsGPUPresentMode,
        SetGPUAllowedFramesInFlight setGPUAllowedFramesInFlight,
        GetGPUSwapchainTextureFormat getGPUSwapchainTextureFormat,
        WaitAndAcquireGPUSwapchainTexture waitAndAcquireGPUSwapchainTexture) {

    /// Binds every function above.
    ///
    /// @param lookup the loaded `libgoldberry`
    public static SdlGpuSwapchainCalls bind(SymbolLookup lookup) {
        return new SdlGpuSwapchainCalls(
                new ClaimWindowForGPUDevice(lookup),
                new ReleaseWindowFromGPUDevice(lookup),
                new SetGPUSwapchainParameters(lookup),
                new WindowSupportsGPUPresentMode(lookup),
                new SetGPUAllowedFramesInFlight(lookup),
                new GetGPUSwapchainTextureFormat(lookup),
                new WaitAndAcquireGPUSwapchainTexture(lookup));
    }

    /// `SDL_GPU_SWAPCHAINCOMPOSITION_SDR`: 8-bit sRGB, what every window has.
    public static final int COMPOSITION_SDR = 0;

    /// `SDL_GPU_PRESENTMODE_VSYNC`: waits for vertical blank; always supported.
    public static final int PRESENTMODE_VSYNC = 0;

    /// `SDL_GPU_PRESENTMODE_IMMEDIATE`: presents at once, and may tear.
    public static final int PRESENTMODE_IMMEDIATE = 1;

    /// `SDL_GPU_PRESENTMODE_MAILBOX`: the newest frame at vertical blank, no wait.
    public static final int PRESENTMODE_MAILBOX = 2;

    /// Claims a window for a device: gives it a swapchain, with SDR composition
    /// and VSYNC until told otherwise. A window claimed has no window surface.
    ///
    /// `_Bool SDL_ClaimWindowForGPUDevice(void*, void*)`
    public static final class ClaimWindowForGPUDevice {

        private static final MethodHandle FD_SDL_ClaimWindowForGPUDevice =
                Downcalls.link(FunctionDescriptor.of(JAVA_BOOLEAN, ADDRESS, ADDRESS));

        private final MemorySegment address;

        ClaimWindowForGPUDevice(SymbolLookup lookup) {
            this.address = Downcalls.symbol(lookup, "SDL_ClaimWindowForGPUDevice");
        }

        /// Calls `SDL_ClaimWindowForGPUDevice`.
        ///
        /// @param device the device
        /// @param window an `SDL_Window*`, on its own thread
        /// @return false if SDL refused
        public boolean call(MemorySegment device, MemorySegment window) {
            try {
                return (boolean) FD_SDL_ClaimWindowForGPUDevice.invokeExact(address, device, window);
            } catch (Throwable t) {
                throw Downcalls.failure("SDL_ClaimWindowForGPUDevice", t);
            }
        }
    }

    /// Gives a claimed window back: its swapchain is destroyed.
    ///
    /// `void SDL_ReleaseWindowFromGPUDevice(void*, void*)`
    public static final class ReleaseWindowFromGPUDevice {

        private static final MethodHandle FD_SDL_ReleaseWindowFromGPUDevice =
                Downcalls.link(FunctionDescriptor.ofVoid(ADDRESS, ADDRESS));

        private final MemorySegment address;

        ReleaseWindowFromGPUDevice(SymbolLookup lookup) {
            this.address = Downcalls.symbol(lookup, "SDL_ReleaseWindowFromGPUDevice");
        }

        /// Calls `SDL_ReleaseWindowFromGPUDevice`.
        ///
        /// @param device the device
        /// @param window the window
        public void call(MemorySegment device, MemorySegment window) {
            try {
                FD_SDL_ReleaseWindowFromGPUDevice.invokeExact(address, device, window);
            } catch (Throwable t) {
                throw Downcalls.failure("SDL_ReleaseWindowFromGPUDevice", t);
            }
        }
    }

    /// Changes a claimed window's composition and present mode.
    ///
    /// `_Bool SDL_SetGPUSwapchainParameters(void*, void*, uint32_t, uint32_t)`
    public static final class SetGPUSwapchainParameters {

        private static final MethodHandle FD_SDL_SetGPUSwapchainParameters =
                Downcalls.link(FunctionDescriptor.of(JAVA_BOOLEAN, ADDRESS, ADDRESS, JAVA_INT, JAVA_INT));

        private final MemorySegment address;

        SetGPUSwapchainParameters(SymbolLookup lookup) {
            this.address = Downcalls.symbol(lookup, "SDL_SetGPUSwapchainParameters");
        }

        /// Calls `SDL_SetGPUSwapchainParameters`.
        ///
        /// @param device      the device
        /// @param window      a claimed window
        /// @param composition an `SDL_GPUSwapchainComposition`
        /// @param presentMode an `SDL_GPUPresentMode`
        /// @return false if SDL refused
        public boolean call(MemorySegment device, MemorySegment window, int composition, int presentMode) {
            try {
                return (boolean)
                        FD_SDL_SetGPUSwapchainParameters.invokeExact(address, device, window, composition, presentMode);
            } catch (Throwable t) {
                throw Downcalls.failure("SDL_SetGPUSwapchainParameters", t);
            }
        }
    }

    /// Whether a claimed window can present in a mode. VSYNC always can.
    ///
    /// `_Bool SDL_WindowSupportsGPUPresentMode(void*, void*, uint32_t)`
    public static final class WindowSupportsGPUPresentMode {

        private static final MethodHandle FD_SDL_WindowSupportsGPUPresentMode =
                Downcalls.link(FunctionDescriptor.of(JAVA_BOOLEAN, ADDRESS, ADDRESS, JAVA_INT));

        private final MemorySegment address;

        WindowSupportsGPUPresentMode(SymbolLookup lookup) {
            this.address = Downcalls.symbol(lookup, "SDL_WindowSupportsGPUPresentMode");
        }

        /// Calls `SDL_WindowSupportsGPUPresentMode`.
        ///
        /// @param device      the device
        /// @param window      a claimed window
        /// @param presentMode an `SDL_GPUPresentMode`
        /// @return true if it can
        public boolean call(MemorySegment device, MemorySegment window, int presentMode) {
            try {
                return (boolean) FD_SDL_WindowSupportsGPUPresentMode.invokeExact(address, device, window, presentMode);
            } catch (Throwable t) {
                throw Downcalls.failure("SDL_WindowSupportsGPUPresentMode", t);
            }
        }
    }

    /// How many frames the CPU may record ahead of the GPU: fewer is less latency,
    /// more is more throughput. SDL's default is 2.
    ///
    /// `_Bool SDL_SetGPUAllowedFramesInFlight(void*, uint32_t)`
    public static final class SetGPUAllowedFramesInFlight {

        private static final MethodHandle FD_SDL_SetGPUAllowedFramesInFlight =
                Downcalls.link(FunctionDescriptor.of(JAVA_BOOLEAN, ADDRESS, JAVA_INT));

        private final MemorySegment address;

        SetGPUAllowedFramesInFlight(SymbolLookup lookup) {
            this.address = Downcalls.symbol(lookup, "SDL_SetGPUAllowedFramesInFlight");
        }

        /// Calls `SDL_SetGPUAllowedFramesInFlight`.
        ///
        /// @param device the device
        /// @param frames from 1 to 3
        /// @return false if SDL refused
        public boolean call(MemorySegment device, int frames) {
            try {
                return (boolean) FD_SDL_SetGPUAllowedFramesInFlight.invokeExact(address, device, frames);
            } catch (Throwable t) {
                throw Downcalls.failure("SDL_SetGPUAllowedFramesInFlight", t);
            }
        }
    }

    /// The format a claimed window's swapchain textures have.
    ///
    /// `uint32_t SDL_GetGPUSwapchainTextureFormat(void*, void*)`
    public static final class GetGPUSwapchainTextureFormat {

        private static final MethodHandle FD_SDL_GetGPUSwapchainTextureFormat =
                Downcalls.link(FunctionDescriptor.of(JAVA_INT, ADDRESS, ADDRESS));

        private final MemorySegment address;

        GetGPUSwapchainTextureFormat(SymbolLookup lookup) {
            this.address = Downcalls.symbol(lookup, "SDL_GetGPUSwapchainTextureFormat");
        }

        /// Calls `SDL_GetGPUSwapchainTextureFormat`.
        ///
        /// @param device the device
        /// @param window a claimed window
        /// @return an `SDL_GPUTextureFormat`, `INVALID` (0) on failure
        public int call(MemorySegment device, MemorySegment window) {
            try {
                return (int) FD_SDL_GetGPUSwapchainTextureFormat.invokeExact(address, device, window);
            } catch (Throwable t) {
                throw Downcalls.failure("SDL_GetGPUSwapchainTextureFormat", t);
            }
        }
    }

    /// Waits for a claimed window's next swapchain texture, for this command buffer
    /// to render into. It is presented when the command buffer is submitted. A
    /// minimised or occluded window gives NULL, which is not a failure.
    ///
    /// `_Bool SDL_WaitAndAcquireGPUSwapchainTexture(void*, void*, void*, void*, void*)`
    public static final class WaitAndAcquireGPUSwapchainTexture {

        private static final MethodHandle FD_SDL_WaitAndAcquireGPUSwapchainTexture =
                Downcalls.link(FunctionDescriptor.of(JAVA_BOOLEAN, ADDRESS, ADDRESS, ADDRESS, ADDRESS, ADDRESS));

        private final MemorySegment address;

        WaitAndAcquireGPUSwapchainTexture(SymbolLookup lookup) {
            this.address = Downcalls.symbol(lookup, "SDL_WaitAndAcquireGPUSwapchainTexture");
        }

        /// Calls `SDL_WaitAndAcquireGPUSwapchainTexture`.
        ///
        /// @param commandBuffer the command buffer
        /// @param window        a claimed window
        /// @param texture       an `SDL_GPUTexture**` the texture is written to, NULL when there is none
        /// @param width         a `Uint32*` for its width, or NULL
        /// @param height        a `Uint32*` for its height, or NULL
        /// @return false on failure
        public boolean call(
                MemorySegment commandBuffer,
                MemorySegment window,
                MemorySegment texture,
                MemorySegment width,
                MemorySegment height) {
            try {
                return (boolean) FD_SDL_WaitAndAcquireGPUSwapchainTexture.invokeExact(
                        address, commandBuffer, window, texture, width, height);
            } catch (Throwable t) {
                throw Downcalls.failure("SDL_WaitAndAcquireGPUSwapchainTexture", t);
            }
        }
    }
}
