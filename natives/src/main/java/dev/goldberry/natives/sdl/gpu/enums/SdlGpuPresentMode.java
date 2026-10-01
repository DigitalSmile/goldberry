package dev.goldberry.natives.sdl.gpu.enums;

import dev.goldberry.natives.sdl.calls.SdlGpuSwapchainCalls;

/// How a claimed window presents, as SDL's `SDL_GPUPresentMode`.
public enum SdlGpuPresentMode {
    /// Waits for vertical blank. Every window supports it, and it is the mode a
    /// window is claimed in.
    VSYNC(SdlGpuSwapchainCalls.PRESENTMODE_VSYNC),
    /// Presents at once, and may tear.
    IMMEDIATE(SdlGpuSwapchainCalls.PRESENTMODE_IMMEDIATE),
    /// Shows the newest frame at vertical blank, without making the CPU wait.
    MAILBOX(SdlGpuSwapchainCalls.PRESENTMODE_MAILBOX);

    private final int value;

    SdlGpuPresentMode(int value) {
        this.value = value;
    }

    /// SDL's value.
    public int value() {
        return value;
    }
}
