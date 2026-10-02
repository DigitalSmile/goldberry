package dev.goldberry.natives.sdl.gpu;

import java.lang.foreign.MemorySegment;
import java.util.Optional;

import dev.goldberry.natives.sdl.gpu.enums.SdlGpuTextureFormat;

/// A claimed window's texture for one frame: rendered or blitted into, and
/// presented when the command buffer that acquired it is submitted.
///
/// SDL owns it. It is valid only in the command buffer that acquired it, and only
/// until that buffer is submitted or cancelled; using it anywhere else fails
/// here rather than in the driver.
///
/// Read more: [The native boundary](https://goldberry.dev/docs/overview/architecture.html#the-native-boundary).
public final class SdlGpuSwapchainTexture implements SdlGpuTarget {

    private final SdlGpuCommandBuffer owner;
    private final MemorySegment handle;
    private final int width;
    private final int height;
    private final Optional<SdlGpuTextureFormat> format;

    SdlGpuSwapchainTexture(
            SdlGpuCommandBuffer owner,
            MemorySegment handle,
            int width,
            int height,
            Optional<SdlGpuTextureFormat> format) {
        this.owner = owner;
        this.handle = handle;
        this.width = width;
        this.height = height;
        this.format = format;
    }

    /// Its format, or empty when it is one this package does not model.
    public Optional<SdlGpuTextureFormat> format() {
        return format;
    }

    @Override
    public int width() {
        return width;
    }

    @Override
    public int height() {
        return height;
    }

    /// The SDL handle, for `commands`.
    ///
    /// @throws IllegalArgumentException when `commands` did not acquire it
    /// @throws IllegalStateException    when its command buffer is finished
    MemorySegment handle(SdlGpuCommandBuffer commands) {
        if (commands != owner) {
            throw new IllegalArgumentException(this + " belongs to another command buffer");
        }
        if (owner.isFinished()) {
            throw new IllegalStateException(this + " was presented or discarded with its command buffer");
        }
        return handle;
    }

    @Override
    public String toString() {
        return "SdlGpuSwapchainTexture[" + width + "x" + height + "]";
    }
}
