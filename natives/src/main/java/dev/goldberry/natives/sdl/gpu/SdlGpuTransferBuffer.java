package dev.goldberry.natives.sdl.gpu;

import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;

import org.jspecify.annotations.Nullable;

import dev.goldberry.natives.sdl.Sdl;
import dev.goldberry.natives.sdl.SdlException;
import dev.goldberry.natives.sdl.gpu.enums.SdlGpuTransferUsage;

/// Memory pixels travel through between the CPU and a texture.
///
/// An upload buffer is written while mapped and then copied into a texture in a
/// copy pass; a download buffer is copied into in a copy pass and read while
/// mapped, once the command buffer's fence has signalled.
///
/// **Mapped memory is safe to hold wrongly.** [#map] returns a [ByteBuffer] over
/// SDL's pointer, scoped to an arena of its own that [#unmap] closes. A buffer
/// kept past `unmap` throws `IllegalStateException` on its next access, where a
/// raw pointer would read freed memory or crash the process.
public final class SdlGpuTransferBuffer extends SdlGpuResource {

    private final SdlGpuTransferUsage usage;
    private final int size;
    private @Nullable Arena mapping;

    SdlGpuTransferBuffer(SdlGpuDevice device, MemorySegment handle, SdlGpuTransferUsage usage, int size) {
        super(device, handle);
        this.usage = usage;
        this.size = size;
    }

    /// Which way this buffer carries pixels.
    public SdlGpuTransferUsage usage() {
        return usage;
    }

    /// Its size in bytes.
    public int size() {
        return size;
    }

    /// Whether it is mapped now.
    public boolean isMapped() {
        return mapping != null;
    }

    /// Maps the buffer, in native byte order.
    ///
    /// @param cycle for an upload buffer the GPU may still be reading from:
    ///              take fresh memory rather than wait for it. Ignored the
    ///              first time a buffer is mapped
    /// @throws IllegalStateException when it is already mapped
    /// @throws SdlException          when SDL refuses
    @SuppressWarnings("restricted")
    public ByteBuffer map(boolean cycle) {
        if (mapping != null) {
            throw new IllegalStateException(this + " is already mapped");
        }
        var pointer = device().calls().resources().mapGPUTransferBuffer().call(device().handle(), handle(), cycle);
        if (MemorySegment.NULL.equals(pointer)) {
            throw new SdlException("SDL_MapGPUTransferBuffer", Sdl.get().lastError());
        }
        var arena = Arena.ofShared();
        mapping = arena;
        return pointer.reinterpret(size, arena, null).asByteBuffer().order(ByteOrder.nativeOrder());
    }

    /// Unmaps the buffer. Every [ByteBuffer] [#map] returned stops working.
    /// Does nothing when it is not mapped.
    public void unmap() {
        var arena = mapping;
        if (arena == null) {
            return;
        }
        mapping = null;
        arena.close();
        device().calls().resources().unmapGPUTransferBuffer().call(device().handle(), handle());
    }

    @Override
    void release(MemorySegment device, MemorySegment handle) {
        var arena = mapping;
        if (arena != null) {
            mapping = null;
            arena.close();
            device().calls().resources().unmapGPUTransferBuffer().call(device, handle);
        }
        device().calls().resources().releaseGPUTransferBuffer().call(device, handle);
    }

    @Override
    public String toString() {
        return "SdlGpuTransferBuffer[" + usage + ", " + size + " bytes]";
    }
}
