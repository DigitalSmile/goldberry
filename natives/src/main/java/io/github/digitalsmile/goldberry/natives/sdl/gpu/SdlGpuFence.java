package io.github.digitalsmile.goldberry.natives.sdl.gpu;

import static java.lang.foreign.ValueLayout.ADDRESS;

import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;

import io.github.digitalsmile.goldberry.natives.sdl.Sdl;
import io.github.digitalsmile.goldberry.natives.sdl.SdlException;

/// Signals when the GPU has finished a submitted command buffer: how a readback
/// knows its pixels have arrived.
public final class SdlGpuFence extends SdlGpuResource {

    SdlGpuFence(SdlGpuDevice device, MemorySegment handle) {
        super(device, handle);
    }

    /// Blocks until the GPU has finished the command buffer.
    ///
    /// @throws SdlException when SDL reports the wait failed: a lost device
    public void await() {
        try (var arena = Arena.ofConfined()) {
            var fences = arena.allocate(ADDRESS);
            fences.set(ADDRESS, 0, handle());
            if (!device().calls().commands().waitForGPUFences().call(device().handle(), true, fences, 1)) {
                throw new SdlException("SDL_WaitForGPUFences", Sdl.get().lastError());
            }
        }
    }

    @Override
    void release(MemorySegment device, MemorySegment handle) {
        device().calls().commands().releaseGPUFence().call(device, handle);
    }

    @Override
    public String toString() {
        return "SdlGpuFence";
    }
}
