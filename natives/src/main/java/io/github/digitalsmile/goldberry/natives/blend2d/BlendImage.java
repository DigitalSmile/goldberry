package io.github.digitalsmile.goldberry.natives.blend2d;

import io.github.digitalsmile.goldberry.natives.layout.Layouts;
import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.nio.ByteBuffer;
import java.util.Objects;

/// A Blend2D image over pixels somebody else owns.
///
/// Goldberry never asks Blend2D to allocate a frame. The buffer already exists —
/// it is the compositor's own surface when the platform lends one, and
/// `:core`'s otherwise — and this wraps it in place. That is the difference
/// between one frame of memory traffic per frame and none, and it is why the
/// only constructor here is the external-data one.
///
/// **The buffer must outlive the image.** Blend2D is told to free nothing, so
/// nothing here reference-counts the pixels: if the platform reclaims its
/// surface while an image still points at it, the next fill writes into memory
/// that is no longer ours. In the frame path that window is exactly between
/// acquiring a frame and presenting it, which is why the image is created and
/// closed inside a single paint.
///
/// Confined to the thread that created it, and must be closed.
public final class BlendImage implements AutoCloseable {

    private final Blend2D blend2d = Blend2D.get();
    private final Arena arena;
    private final MemorySegment image;
    private final Thread owner = Thread.currentThread();
    private final int width;
    private final int height;
    private final int stride;

    private boolean closed;

    private BlendImage(ByteBuffer pixels, int width, int height, int stride, BlendFormat format) {
        this.width = width;
        this.height = height;
        this.stride = stride;
        this.arena = Arena.ofConfined();
        try {
            // Every Blend2D core object is one BLObjectDetail and nothing else.
            // The layout table asserts that BLImageCore really is that shape.
            this.image = arena.allocate(Layouts.BL_OBJECT_DETAIL.layout());
            blend2d.imageInitFromData(
                    image, width, height, format, addressOf(pixels), stride);
        } catch (RuntimeException | Error e) {
            arena.close();
            throw e;
        }
    }

    /// Wraps an existing buffer as a premultiplied 32-bit image.
    ///
    /// The buffer is **not** copied and **not** owned. Its contents are whatever
    /// they already were — this does not clear them, because the frame path
    /// often wants the previous frame still there.
    ///
    /// @param pixels a direct buffer holding the pixels, in BGRA memory order
    /// @param width  width in physical pixels
    /// @param height height in physical pixels
    /// @param stride bytes per row, which may exceed `width * 4`
    /// @throws IllegalArgumentException if the buffer is not direct, or is too
    ///         small for the size and stride described
    public static BlendImage wrapping(ByteBuffer pixels, int width, int height, int stride) {
        return wrapping(pixels, width, height, stride, BlendFormat.PRGB32);
    }

    /// Wraps an existing buffer in a given format.
    ///
    /// @see #wrapping(ByteBuffer, int, int, int)
    public static BlendImage wrapping(
            ByteBuffer pixels, int width, int height, int stride, BlendFormat format) {

        Objects.requireNonNull(pixels, "pixels");
        Objects.requireNonNull(format, "format");
        if (width <= 0 || height <= 0) {
            throw new IllegalArgumentException(
                    "an image must have a positive size, and " + width + "x" + height + " does not");
        }
        if (stride < Math.multiplyExact(width, 4)) {
            throw new IllegalArgumentException(
                    "a stride of " + stride + " cannot hold a row of " + width
                            + " 32-bit pixels, which needs " + (width * 4));
        }
        // The last row does not need padding after it, so the requirement is
        // one row short of stride * height. Getting this wrong the other way
        // would reject a legal tightly-packed buffer.
        var required = Math.addExact(
                Math.multiplyExact((long) stride, height - 1), Math.multiplyExact(width, 4L));
        // `remaining`, not `capacity`: MemorySegment.ofBuffer honours the
        // buffer's position and limit, so a buffer positioned partway through
        // gives Blend2D fewer bytes than its capacity suggests.
        if (pixels.remaining() < required) {
            throw new IllegalArgumentException(
                    "a " + width + "x" + height + " image at stride " + stride + " needs "
                            + required + " bytes, and the buffer offers " + pixels.remaining());
        }
        return new BlendImage(pixels, width, height, stride, format);
    }

    /// Width in physical pixels.
    public int width() {
        return width;
    }

    /// Height in physical pixels.
    public int height() {
        return height;
    }

    /// Bytes per row.
    public int stride() {
        return stride;
    }

    /// Whether the image has been closed.
    public boolean isClosed() {
        return closed;
    }

    /// Releases Blend2D's side of the image. The pixels are untouched — they were
    /// never Blend2D's to free.
    ///
    /// @throws IllegalStateException if a context is still rendering into it
    @Override
    public void close() {
        if (closed) {
            return;
        }
        requireOwner();
        closed = true;
        try {
            blend2d.imageDestroy(image);
        } finally {
            arena.close();
        }
    }

    MemorySegment pointer() {
        requireOwner();
        if (closed) {
            throw new IllegalStateException("this BlendImage has been closed");
        }
        return image;
    }

    /// Where Blend2D thinks the pixels are. Package-private, for the test that
    /// asserts this really is a view rather than a copy.
    Blend2D.ImageData data() {
        return blend2d.imageData(pointer());
    }

    /// The address of a direct buffer's contents.
    ///
    /// A heap buffer has no stable address at all — the collector may move it
    /// between the check and the call — so it is refused rather than copied.
    /// Copying would silently give back an image that paints into memory the
    /// caller never sees.
    private static MemorySegment addressOf(ByteBuffer pixels) {
        if (!pixels.isDirect()) {
            throw new IllegalArgumentException(
                    "the pixel buffer must be direct: a heap buffer has no address Blend2D could"
                            + " keep, and copying it would paint into memory nobody presents");
        }
        return MemorySegment.ofBuffer(pixels);
    }

    private void requireOwner() {
        if (Thread.currentThread() != owner) {
            throw new IllegalStateException(
                    "a BlendImage belongs to the thread that created it, and this is not it");
        }
    }

    @Override
    public String toString() {
        return "BlendImage[" + width + "x" + height + " @" + stride + (closed ? ", closed" : "") + "]";
    }
}
