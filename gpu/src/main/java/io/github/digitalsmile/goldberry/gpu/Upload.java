package io.github.digitalsmile.goldberry.gpu;

import java.nio.ByteBuffer;

import org.jspecify.annotations.Nullable;

import io.github.digitalsmile.goldberry.natives.sdl.gpu.SdlGpuDevice;
import io.github.digitalsmile.goldberry.natives.sdl.gpu.SdlGpuTransferBuffer;
import io.github.digitalsmile.goldberry.natives.sdl.gpu.SdlGpuTransferUsage;

/// The staging memory every upload of a device passes through: one transfer
/// buffer, sized to the largest upload seen and grown by doubling, and cycled
/// (`docs/gpu-plan.md`, phase 2).
///
/// **Cycled** means each [#map] asks SDL for fresh memory when the GPU may still
/// be reading what the last one wrote -- a frame in flight, or an earlier upload
/// in the same copy pass -- so writing never waits for the GPU and never
/// overwrites bytes it has yet to copy. SDL keeps the few buffers that takes
/// behind the one handle and reuses them once the GPU is done, so a steady
/// frame rate settles on frames-in-flight plus one of them.
///
/// **Grown** means a buffer too small for an upload is released (SDL frees it
/// when the GPU is done with it) and replaced by one of at least twice the size,
/// so a window that is resized a pixel at a time does not allocate a pixel at
/// a time.
final class Upload {

    /// The smallest buffer made: 64 KiB, which a caret's damage or a mesh fits.
    static final int MINIMUM_CAPACITY = 64 * 1024;

    private final SdlGpuDevice device;
    private @Nullable SdlGpuTransferBuffer buffer;

    Upload(SdlGpuDevice device) {
        this.device = device;
    }

    /// The size a buffer holding `needed` bytes grows to from `capacity`: at
    /// least [#MINIMUM_CAPACITY], at least double, at least `needed`, and at
    /// most `Integer.MAX_VALUE`, which is as large as SDL's sizes go.
    ///
    /// @throws IllegalArgumentException when `needed` is past that
    static int grow(int capacity, long needed) {
        if (needed > Integer.MAX_VALUE) {
            throw new IllegalArgumentException("an upload of " + needed + " bytes is past SDL's 2 GiB");
        }
        var doubled = Math.min(2L * capacity, Integer.MAX_VALUE);
        return (int) Math.max(needed, Math.max(doubled, MINIMUM_CAPACITY));
    }

    /// The current buffer's size, or 0 before the first upload.
    int capacity() {
        var current = buffer;
        return current == null || current.isClosed() ? 0 : current.size();
    }

    /// Maps at least `bytes` bytes to write an upload into, from index 0.
    /// [#unmap] must follow before the copy pass records the upload from
    /// [#buffer].
    ///
    /// @throws io.github.digitalsmile.goldberry.natives.sdl.SdlException when
    ///         SDL refuses the buffer or the mapping
    ByteBuffer map(long bytes) {
        var current = buffer;
        if (current == null || current.isClosed() || current.size() < bytes) {
            var size = grow(capacity(), bytes);
            if (current != null) {
                current.close();
            }
            current = device.createTransferBuffer(SdlGpuTransferUsage.UPLOAD, size);
            buffer = current;
        }
        return current.map(true);
    }

    /// Unmaps what [#map] mapped. Does nothing when nothing is mapped.
    void unmap() {
        var current = buffer;
        if (current != null) {
            current.unmap();
        }
    }

    /// The buffer the last [#map] wrote into, unmapped, to record the upload
    /// from.
    ///
    /// @throws IllegalStateException when nothing was mapped, or it is still
    ///                               mapped
    SdlGpuTransferBuffer buffer() {
        var current = buffer;
        if (current == null || current.isMapped()) {
            throw new IllegalStateException("no upload is staged, or it is still mapped");
        }
        return current;
    }
}
