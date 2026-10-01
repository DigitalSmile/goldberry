package dev.goldberry.gpu.render;

import java.nio.ByteBuffer;
import java.util.List;

import org.jspecify.annotations.Nullable;

import dev.goldberry.natives.sdl.gpu.SdlGpuDevice;
import dev.goldberry.natives.sdl.gpu.SdlGpuRegion;
import dev.goldberry.natives.sdl.gpu.SdlGpuTransferBuffer;
import dev.goldberry.natives.sdl.gpu.enums.SdlGpuTransferUsage;

/// The staging memory a device's uploads pass through: one transfer buffer,
/// sized to the largest upload seen and grown by doubling, and cycled
/// (`docs/gpu-plan.md`, phase 2; ADR-0478). The public API's `CopyPass` and
/// the composited window's UI upload share one per device.
///
/// **Cycled** means each stage asks SDL for fresh memory when the GPU may still
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
///
/// The callers check what they stage: regions inside the image, an image long
/// enough. What is staged is unmapped again before a stage returns, whether or
/// not it threw, so the copy pass can record from [#buffer].
public final class StagingBuffer {

    /// The smallest buffer made: 64 KiB, which a caret's damage or a mesh fits.
    public static final int MINIMUM_CAPACITY = 64 * 1024;

    private final SdlGpuDevice device;
    private @Nullable SdlGpuTransferBuffer buffer;

    /// Staging memory for `device`, made at the first stage.
    public StagingBuffer(SdlGpuDevice device) {
        this.device = device;
    }

    /// The size a buffer holding `needed` bytes grows to from `capacity`: at
    /// least [#MINIMUM_CAPACITY], at least double, at least `needed`, and at
    /// most `Integer.MAX_VALUE`, which is as large as SDL's sizes go.
    ///
    /// @throws IllegalArgumentException when `needed` is past that
    public static int grow(int capacity, long needed) {
        if (needed > Integer.MAX_VALUE) {
            throw new IllegalArgumentException("an upload of " + needed + " bytes is past SDL's 2 GiB");
        }
        var doubled = Math.min(2L * capacity, Integer.MAX_VALUE);
        return (int) Math.max(needed, Math.max(doubled, MINIMUM_CAPACITY));
    }

    /// The current buffer's size, or 0 before the first stage.
    public int capacity() {
        var current = buffer;
        return current == null || current.isClosed() ? 0 : current.size();
    }

    /// Copies `regions` of an image into fresh staging memory, each region's
    /// rows packed one after another and the regions one after another. Row `y`
    /// of the image starts `y × rowBytes` bytes past `image`'s position, which is
    /// not moved.
    ///
    /// @return each region's byte offset in [#buffer]
    /// @throws dev.goldberry.natives.sdl.SdlException when SDL
    ///         refuses the memory
    public int[] stage(ByteBuffer image, int rowBytes, int bytesPerPixel, List<SdlGpuRegion> regions) {
        var total = 0L;
        for (var region : regions) {
            total += (long) region.width() * region.height() * bytesPerPixel;
        }
        var staging = map(total);
        var offsets = new int[regions.size()];
        var base = image.position();
        var at = 0;
        try {
            for (var i = 0; i < regions.size(); i++) {
                var region = regions.get(i);
                offsets[i] = at;
                var row = region.width() * bytesPerPixel;
                for (var y = region.y(); y < region.y() + region.height(); y++) {
                    staging.put(at, image, base + y * rowBytes + region.x() * bytesPerPixel, row);
                    at += row;
                }
            }
        } finally {
            unmap();
        }
        return offsets;
    }

    /// Copies the remaining bytes of `source`, which is not moved, into fresh
    /// staging memory from offset 0.
    ///
    /// @throws dev.goldberry.natives.sdl.SdlException when SDL
    ///         refuses the memory
    public void stage(ByteBuffer source) {
        var size = source.remaining();
        var staging = map(size);
        try {
            staging.put(0, source, source.position(), size);
        } finally {
            unmap();
        }
    }

    /// The buffer the last stage wrote into, to record the upload from.
    ///
    /// @throws IllegalStateException when nothing was staged
    public SdlGpuTransferBuffer buffer() {
        var current = buffer;
        if (current == null || current.isMapped() || current.isClosed()) {
            throw new IllegalStateException("nothing is staged");
        }
        return current;
    }

    /// Maps at least `bytes` bytes, cycled, growing the buffer first when it is
    /// too small.
    private ByteBuffer map(long bytes) {
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

    private void unmap() {
        var current = buffer;
        if (current != null) {
            current.unmap();
        }
    }
}
