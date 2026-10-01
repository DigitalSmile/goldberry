package dev.goldberry.natives.sdl.gpu;

import java.lang.foreign.MemorySegment;
import java.util.Optional;

import dev.goldberry.natives.sdl.Sdl;
import dev.goldberry.natives.sdl.SdlException;
import dev.goldberry.natives.sdl.SdlWindowHandle;
import dev.goldberry.natives.sdl.calls.SdlGpuSwapchainCalls;
import dev.goldberry.natives.sdl.gpu.enums.SdlGpuPresentMode;
import dev.goldberry.natives.sdl.gpu.enums.SdlGpuTextureFormat;

/// A window claimed by a [SdlGpuDevice]: it presents through a swapchain, and has
/// no window surface while it is claimed (`docs/gpu-plan.md`, D3).
///
/// Closing it gives the window back, and the window surface can be asked for
/// again. It is claimed and released on the window's own thread, as SDL
/// requires. A device that closes releases the windows it still holds.
public final class SdlGpuWindow extends SdlGpuResource {

    private final SdlWindowHandle window;
    private SdlGpuPresentMode presentMode = SdlGpuPresentMode.VSYNC;

    SdlGpuWindow(SdlGpuDevice device, MemorySegment pointer, SdlWindowHandle window) {
        super(device, pointer);
        this.window = window;
    }

    /// The window claimed.
    public SdlWindowHandle window() {
        return window;
    }

    /// The format of the swapchain's textures, or empty when it is one this
    /// package does not model.
    public Optional<SdlGpuTextureFormat> textureFormat() {
        var value = device().calls().swapchain().getGPUSwapchainTextureFormat().call(device().handle(), handle());
        for (var format : SdlGpuTextureFormat.values()) {
            if (format.value() == value) {
                return Optional.of(format);
            }
        }
        return Optional.empty();
    }

    /// Whether this window can present in `mode`. [SdlGpuPresentMode#VSYNC]
    /// always can.
    public boolean supports(SdlGpuPresentMode mode) {
        return device().calls()
                .swapchain()
                .windowSupportsGPUPresentMode()
                .call(device().handle(), handle(), mode.value());
    }

    /// The mode it presents in now.
    public SdlGpuPresentMode presentMode() {
        return presentMode;
    }

    /// Presents in `mode` from the next frame, with SDR composition.
    ///
    /// @throws IllegalArgumentException when the window does not support it
    /// @throws SdlException             when SDL refuses
    public void setPresentMode(SdlGpuPresentMode mode) {
        if (!supports(mode)) {
            throw new IllegalArgumentException(window + " cannot present in " + mode);
        }
        var swapchain = device().calls().swapchain();
        if (!swapchain
                .setGPUSwapchainParameters()
                .call(device().handle(), handle(), SdlGpuSwapchainCalls.COMPOSITION_SDR, mode.value())) {
            throw new SdlException("SDL_SetGPUSwapchainParameters", Sdl.get().lastError());
        }
        presentMode = mode;
    }

    /// The `SDL_Window*`, for the command buffer's acquire.
    MemorySegment pointer() {
        return handle();
    }

    @Override
    void release(MemorySegment device, MemorySegment handle) {
        device().calls().swapchain().releaseWindowFromGPUDevice().call(device, handle);
    }

    @Override
    public String toString() {
        return "SdlGpuWindow[" + window + "]";
    }
}
