package io.github.digitalsmile.goldberry.natives.blend2d;

import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.nio.ByteBuffer;
import java.util.Objects;

import io.github.digitalsmile.goldberry.natives.blend2d.enums.BlendImageScaleFilter;
import io.github.digitalsmile.goldberry.natives.layout.Layouts;

/// An image resampled to another size, into pixels Blend2D allocated.
///
/// **The second place Goldberry lets Blend2D own pixels**, after
/// [BlendDecodedImage], and for a weaker reason than that one had: a decode
/// *must* allocate because the size of a PNG is inside the PNG, whereas a
/// resample's size is the caller's own argument. What makes it allocate anyway
/// is that `bl_image_scale` takes a destination `BLImageCore` and resizes it
/// itself — there is no form of the call that writes into a buffer somebody
/// else owns (ADR-0428).
///
/// So the discipline ADR-0283 set for the decoder is the one that applies here,
/// word for word: the allocation is meant to be short-lived, and the intended
/// use is the whole of it:
///
/// ```java
/// try (var scaled = BlendScaledImage.scale(source, 64, 64, BlendImageScaleFilter.LANCZOS)) {
///     var buffer = // allocate 64 x 64
///     scaled.copyInto(buffer, stride);
/// }
/// ```
///
/// after which the pixels are Java's and behave like every other buffer here.
///
/// ## What is resampled is premultiplied
///
/// The source is whatever format it already was, which everywhere in this
/// toolkit is premultiplied BGRA, and Blend2D gives the destination the source's
/// format. Premultiplied is the **right** space to filter in: averaging straight
/// alpha would weight a fully transparent pixel's colour as if it were there,
/// which is what puts a dark halo around a resampled cut-out.
///
/// A filter with negative lobes — [BlendImageScaleFilter#LANCZOS],
/// [BlendImageScaleFilter#BICUBIC] — can overshoot and leave a channel above the
/// alpha it is premultiplied by, which is not a representable colour. Reading
/// such a pixel back clamps it; nothing here pretends it did not happen.
///
/// Confined to the thread that created it, and must be closed.
public final class BlendScaledImage implements AutoCloseable {

    private final Blend2dImage calls = Blend2dImage.get();
    private final Arena arena;
    private final MemorySegment image;
    private final Thread owner = Thread.currentThread();
    private final int width;
    private final int height;

    private boolean closed;

    private BlendScaledImage(BlendImage source, int width, int height, BlendImageScaleFilter filter) {
        this.width = width;
        this.height = height;
        this.arena = Arena.ofConfined();
        try {
            this.image = arena.allocate(Layouts.BL_OBJECT_DETAIL.layout());
            // Initialised first and unconditionally, for `BlendDecodedImage`'s
            // reason: `bl_image_scale` writes into an image that already exists,
            // and a refused scale leaves an empty one that still has to be
            // destroyed.
            calls.imageInit(image);
            try {
                calls.imageScale(image, source.pointer(), width, height, filter);
            } catch (RuntimeException | Error e) {
                calls.imageDestroy(image);
                throw e;
            }
        } catch (RuntimeException | Error e) {
            arena.close();
            throw e;
        }
    }

    /// Resamples `source` to `width` × `height`.
    ///
    /// The size is the caller's and is not checked against the source's: making
    /// an image larger is as legal as making it smaller, and which filter suits
    /// which direction is [BlendImageScaleFilter]'s subject rather than this
    /// method's.
    ///
    /// @param source the image to read; not modified, and still the caller's to
    ///        close
    /// @throws IllegalArgumentException if the size is not positive
    /// @throws io.github.digitalsmile.goldberry.natives.blend2d.error.BlendException
    ///         if Blend2D refuses — running out of memory for the destination is
    ///         the ordinary reason
    public static BlendScaledImage scale(BlendImage source, int width, int height, BlendImageScaleFilter filter) {
        Objects.requireNonNull(source, "source");
        Objects.requireNonNull(filter, "filter");
        if (width <= 0 || height <= 0) {
            throw new IllegalArgumentException(
                    "a scaled image needs a positive size, and " + width + "x" + height + " is not");
        }
        return new BlendScaledImage(source, width, height, filter);
    }

    /// Width in pixels — the size that was asked for.
    public int width() {
        return width;
    }

    /// Height in pixels.
    public int height() {
        return height;
    }

    /// Whether this has been closed.
    public boolean isClosed() {
        return closed;
    }

    /// Copies the resampled pixels into `destination`, in the source's format.
    ///
    /// Row by row, because the strides need not agree: Blend2D pads a row to its
    /// own alignment and the destination is whatever the caller allocated.
    ///
    /// @param destination a **direct** buffer with room for the rows described
    /// @param stride bytes per row in `destination`, at least `width * 4`
    /// @throws IllegalArgumentException if the buffer is not direct, or is too
    ///         small for the image at that stride
    /// @throws IllegalStateException if this has been closed, or the calling
    ///         thread is not the one that scaled it
    public void copyInto(ByteBuffer destination, int stride) {
        Objects.requireNonNull(destination, "destination");
        requireUsable();
        if (!destination.isDirect()) {
            throw new IllegalArgumentException(
                    "the destination must be direct: a heap buffer has no address the copy could"
                            + " target, and a collector may move it mid-copy");
        }
        var rowBytes = Math.multiplyExact(width, 4);
        if (stride < rowBytes) {
            throw new IllegalArgumentException("a stride of " + stride + " cannot hold a row of " + width
                    + " 32-bit pixels, which needs " + rowBytes);
        }
        var required = Math.addExact(Math.multiplyExact((long) stride, height - 1), rowBytes);
        if (destination.remaining() < required) {
            throw new IllegalArgumentException("a " + width + "x" + height + " image at stride " + stride + " needs "
                    + required + " bytes, and the buffer offers " + destination.remaining());
        }
        calls.copyPixels(image, destination, stride);
    }

    /// Releases the pixels Blend2D allocated.
    @Override
    public void close() {
        if (closed) {
            return;
        }
        requireOwner();
        closed = true;
        try {
            calls.imageDestroy(image);
        } finally {
            arena.close();
        }
    }

    private void requireUsable() {
        requireOwner();
        if (closed) {
            throw new IllegalStateException("this BlendScaledImage has been closed");
        }
    }

    private void requireOwner() {
        if (Thread.currentThread() != owner) {
            throw new IllegalStateException(
                    "a BlendScaledImage belongs to the thread that scaled it, and this is not it");
        }
    }

    @Override
    public String toString() {
        return "BlendScaledImage[" + width + "x" + height + (closed ? ", closed" : "") + "]";
    }
}
