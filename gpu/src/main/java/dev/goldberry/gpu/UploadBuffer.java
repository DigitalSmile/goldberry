package dev.goldberry.gpu;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

import dev.goldberry.natives.sdl.gpu.SdlGpuTransferBuffer;

/// Upload memory on a [GpuDevice]: bytes written by the CPU and copied into a
/// texture by a [CopyPass] with no copy of their own in between.
///
/// What a producer fills that is not the device's thread: a video decoder
/// writes a picture's planes straight into a mapped buffer, and the device's
/// thread uploads them without touching them. Every call is the device's
/// thread's, as everywhere in this API; only the bytes of the [ByteBuffer]
/// [#map] hands back may be written from another thread, while the buffer
/// stays mapped, and a handover between the two threads is the caller's to
/// order. Unmapping, or closing the buffer or its device, ends the mapping
/// for every thread: a write through it afterwards fails in Java and reaches
/// no freed memory.
///
/// A copy pass uploads from a buffer only while it is unmapped. Mapping it
/// again with `cycle` gives fresh memory where the GPU may still be reading the
/// last bytes, so writing never waits for the GPU and never overwrites what it
/// has yet to copy; without `cycle` the mapping is the same memory, and its
/// bytes are still there to read.
public final class UploadBuffer extends GpuResource {

    private final SdlGpuTransferBuffer sdl;

    UploadBuffer(GpuDevice device, SdlGpuTransferBuffer sdl) {
        super(device, sdl);
        this.sdl = sdl;
    }

    /// Its size in bytes.
    public int size() {
        return sdl.size();
    }

    /// Maps the buffer for writing, little-endian.
    ///
    /// @param cycle take fresh memory where the GPU may still read the old;
    ///              ignored the first time a buffer is mapped
    /// @throws IllegalStateException when it is mapped already, or closed
    /// @throws WrongThreadException  off the device's thread
    /// @throws GpuException          when the driver refuses
    public ByteBuffer map(boolean cycle) {
        requireUsableBy(device());
        return GpuDevice.call(() -> sdl.map(cycle)).order(ByteOrder.LITTLE_ENDIAN);
    }

    /// Unmaps it: every buffer [#map] handed back stops working. Does nothing
    /// when it is not mapped.
    ///
    /// @throws WrongThreadException off the device's thread
    public void unmap() {
        device().requireThread();
        sdl.unmap();
    }

    /// Whether it is mapped now.
    public boolean isMapped() {
        return !isClosed() && sdl.isMapped();
    }

    /// The SDL buffer, for `user`'s commands.
    SdlGpuTransferBuffer sdl(GpuDevice user) {
        requireUsableBy(user);
        return sdl;
    }

    @Override
    public String toString() {
        return "UploadBuffer[" + sdl.size() + " bytes" + (isClosed() ? ", closed]" : isMapped() ? ", mapped]" : "]");
    }
}
