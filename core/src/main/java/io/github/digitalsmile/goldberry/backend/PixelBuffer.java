package io.github.digitalsmile.goldberry.backend;

import java.nio.ByteBuffer;
import java.util.Objects;

/// A rasterized frame, ready to hand to a backend.
///
/// This is the CPU presentation path (`docs/ARCHITECTURE.md` §5): Blend2D
/// rasterizes into memory and the backend uploads it. The GPU path bypasses this
/// entirely and is not in this cut of the SPI.
///
/// The pixels cross as a [ByteBuffer] rather than a `MemorySegment`. §3.1 forbids
/// a raw segment escaping the `natives` module, and a `ByteBuffer` is what
/// `MemorySegment.asByteBuffer()` produces without copying — so Blend2D's own
/// memory reaches the backend directly, and `:core` never sees a segment.
///
/// The buffer is **borrowed**, not owned. It is valid for the duration of the
/// `present()` call that carries it and may be reused for the next frame
/// immediately afterwards; a backend that needs to keep the pixels must copy
/// them.
public record PixelBuffer(PhysicalSize size, PixelFormat format, int stride, ByteBuffer pixels) {

    public PixelBuffer {
        Objects.requireNonNull(size, "size");
        Objects.requireNonNull(format, "format");
        Objects.requireNonNull(pixels, "pixels");

        var minimumStride = format.minimumStride(size.width());
        if (stride < minimumStride) {
            throw new IllegalArgumentException(
                    "stride " + stride + " is too small for " + size.width() + " "
                            + format + " pixels; needs at least " + minimumStride);
        }

        // The last row does not need trailing padding, so the requirement is
        // (height - 1) full strides plus one row of pixels -- not height strides.
        // Demanding the larger number would reject a legitimate tightly-allocated
        // buffer, which is exactly what Blend2D hands over.
        var required = size.height() == 0
                ? 0
                : (long) (size.height() - 1) * stride + minimumStride;
        if (pixels.remaining() < required) {
            throw new IllegalArgumentException(
                    "buffer holds " + pixels.remaining() + " bytes, but " + size
                            + " at stride " + stride + " needs " + required);
        }
    }

    /// A tightly packed buffer of the given size, backed by heap memory.
    ///
    /// For tests and the headless backend. Real frames come from Blend2D.
    public static PixelBuffer allocate(PhysicalSize size, PixelFormat format) {
        var stride = format.minimumStride(size.width());
        var bytes = Math.multiplyExact(stride, size.height());
        return new PixelBuffer(size, format, stride, ByteBuffer.allocate(bytes));
    }

    /// Whether rows are contiguous, with no padding between them.
    public boolean isTightlyPacked() {
        return stride == format.minimumStride(size.width());
    }

    /// A read-only view, for a backend that wants to be sure it cannot scribble
    /// on the rasterizer's memory.
    public PixelBuffer asReadOnly() {
        return new PixelBuffer(size, format, stride, pixels.asReadOnlyBuffer());
    }
}
