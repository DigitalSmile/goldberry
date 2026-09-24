package io.github.digitalsmile.goldberry.natives.sdl.gpu;

import java.lang.foreign.MemorySegment;

/// A claimed window's texture for one frame: rendered or blitted into, and
/// presented when the command buffer that acquired it is submitted.
///
/// SDL owns it. It is valid only in the command buffer that acquired it, and only
/// until that buffer is submitted or cancelled; using it anywhere else fails
/// here rather than in the driver.
public final class SdlGpuSwapchainTexture implements SdlGpuTarget {

    private final SdlGpuCommandBuffer owner;
    private final MemorySegment handle;
    private final int width;
    private final int height;

    SdlGpuSwapchainTexture(SdlGpuCommandBuffer owner, MemorySegment handle, int width, int height) {
        this.owner = owner;
        this.handle = handle;
        this.width = width;
        this.height = height;
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
