package io.github.digitalsmile.goldberry.natives.blend2d;

import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.nio.ByteBuffer;
import java.util.Objects;

import io.github.digitalsmile.goldberry.natives.blend2d.enums.BlendFormat;
import io.github.digitalsmile.goldberry.natives.layout.Layouts;

/// Encoded bytes, decoded by Blend2D into pixels it allocated.
///
/// **The one place Goldberry lets Blend2D own pixels**, and the reason is
/// arithmetic nobody can do in advance: the size of a PNG is inside the PNG, so
/// the decoder has to allocate before anything Java could have allocated
/// instead. [BlendImage]'s doctrine — a view over a buffer somebody else owns —
/// holds everywhere else, including for drawing the result of this and for
/// encoding one (ADR-0283).
///
/// The allocation is meant to be short-lived. The intended use is the whole of
/// it:
///
/// ```java
/// try (var decoded = BlendDecodedImage.decode(bytes)) {
///     var buffer = // allocate decoded.width() x decoded.height()
///     decoded.copyInto(buffer, stride);
/// }
/// ```
///
/// after which the pixels are Java's and behave like every other buffer in the
/// toolkit. Keeping one of these open instead would be holding a native
/// allocation for as long as an application holds an image, which is the thing
/// `:core` has never had to do.
///
/// The pixels are converted to **premultiplied BGRA** before they are handed
/// over, whatever the file held: a PNG with no alpha channel decodes to `XRGB32`,
/// and a caller that had to ask would be a caller that could forget.
///
/// Confined to the thread that created it, and must be closed.
public final class BlendDecodedImage implements AutoCloseable {

    private final Blend2dImage calls = Blend2dImage.get();
    private final Arena arena;
    private final MemorySegment image;
    private final Thread owner = Thread.currentThread();
    private final int width;
    private final int height;

    private boolean closed;

    private BlendDecodedImage(ByteBuffer encoded) {
        this.arena = Arena.ofConfined();
        try {
            this.image = arena.allocate(Layouts.BL_OBJECT_DETAIL.layout());
            // Initialised first and unconditionally: `bl_image_read_from_data`
            // writes into an image that already exists, and a failed decode
            // leaves an empty one that still has to be destroyed.
            calls.imageInit(image);
            try {
                calls.imageReadFromData(image, source(encoded), encoded.remaining());
                // Normalising here rather than at the blit. A decoded image is
                // whatever format the file implied -- PRGB32 for a PNG with an
                // alpha channel, XRGB32 for one without -- and every buffer the
                // toolkit blits is premultiplied BGRA.
                calls.imageConvert(image, BlendFormat.PRGB32);
            } catch (RuntimeException | Error e) {
                calls.imageDestroy(image);
                throw e;
            }
            var data = calls.imageData(image);
            this.width = data.width();
            this.height = data.height();
        } catch (RuntimeException | Error e) {
            arena.close();
            throw e;
        }
    }

    /// Decodes PNG, JPEG or QOI bytes.
    ///
    /// The format is recognised from the bytes themselves — Blend2D asks each
    /// built-in codec — so nothing here takes a format argument and a file
    /// renamed to the wrong extension decodes anyway.
    ///
    /// @param encoded the encoded bytes; the buffer's position and limit are
    ///        honoured and its contents are not modified
    /// @throws io.github.digitalsmile.goldberry.natives.blend2d.error.BlendException
    ///         if no codec recognises the bytes, or the image is malformed
    /// @throws IllegalArgumentException if there are no bytes to decode
    public static BlendDecodedImage decode(ByteBuffer encoded) {
        Objects.requireNonNull(encoded, "encoded");
        if (!encoded.hasRemaining()) {
            throw new IllegalArgumentException("there is nothing to decode: the buffer holds no bytes");
        }
        return new BlendDecodedImage(encoded);
    }

    /// Width in pixels, as the file says — not as anybody guessed.
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

    /// Copies the decoded pixels into `destination`, as premultiplied BGRA.
    ///
    /// Row by row, because the strides need not agree: Blend2D pads a row to its
    /// own alignment and the destination is whatever the caller allocated.
    ///
    /// @param destination a **direct** buffer with room for the rows described
    /// @param stride bytes per row in `destination`, at least `width * 4`
    /// @throws IllegalArgumentException if the buffer is not direct, or is too
    ///         small for the image at that stride
    /// @throws IllegalStateException if this has been closed, or the calling
    ///         thread is not the one that decoded it
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
        // The last row needs no padding after it, exactly as BlendImage.wrapping
        // reasons about the buffer it borrows.
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

    /// The address of the encoded bytes, copying a heap buffer into the arena
    /// first.
    ///
    /// Unlike a pixel buffer, a heap array is *fine* here and is the common case:
    /// encoded bytes arrive from `Files.readAllBytes` or a network read, they are
    /// read once during the call, and the copy is bounded by the file size rather
    /// than paid every frame. Refusing them would push the copy onto every caller
    /// instead.
    private MemorySegment source(ByteBuffer encoded) {
        if (encoded.isDirect()) {
            return MemorySegment.ofBuffer(encoded);
        }
        var copy = arena.allocate(encoded.remaining());
        MemorySegment.copy(MemorySegment.ofBuffer(encoded), 0, copy, 0, encoded.remaining());
        return copy;
    }

    private void requireUsable() {
        requireOwner();
        if (closed) {
            throw new IllegalStateException("this BlendDecodedImage has been closed");
        }
    }

    private void requireOwner() {
        if (Thread.currentThread() != owner) {
            throw new IllegalStateException(
                    "a BlendDecodedImage belongs to the thread that decoded it, and this is not it");
        }
    }

    @Override
    public String toString() {
        return "BlendDecodedImage[" + width + "x" + height + (closed ? ", closed" : "") + "]";
    }
}
