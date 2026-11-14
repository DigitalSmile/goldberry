package dev.goldberry.image;

import java.nio.ByteBuffer;
import java.util.Objects;

import dev.goldberry.render.model.PhysicalSize;

/// Memory an image is decoded into: a direct buffer and the bytes from the
/// start of one row to the next.
///
/// ```java
/// var size = Image.decodeInto(bytes, ChannelOrder.RGBA,
///         decoded -> PixelTarget.packed(transfer.map(decoded), decoded.width()));
/// ```
///
/// Rows start at the buffer's position. The last row needs no padding after it.
///
/// @param pixels a direct buffer; its position is where the first row goes
/// @param stride bytes per row, at least four times the width
///
/// Read more: [Text, fonts and icons](https://goldberry.dev/docs/guide/text.html#images).
public record PixelTarget(ByteBuffer pixels, int stride) {

    public PixelTarget {
        Objects.requireNonNull(pixels, "pixels");
        if (!pixels.isDirect()) {
            throw new IllegalArgumentException(
                    "pixels are decoded into a direct buffer, and this one is on the heap: wrap native or"
                            + " mapped memory, or use ByteBuffer.allocateDirect");
        }
        if (stride <= 0) {
            throw new IllegalArgumentException("a stride is a positive number of bytes, and " + stride + " is not");
        }
    }

    /// `pixels` with rows packed tightly, four bytes a pixel.
    public static PixelTarget packed(ByteBuffer pixels, int width) {
        return new PixelTarget(pixels, Math.multiplyExact(width, 4));
    }

    /// How many bytes an image of `size` needs at this stride: every row but
    /// the last at the stride, and the last at its own width.
    public long bytesFor(PhysicalSize size) {
        Objects.requireNonNull(size, "size");
        return (long) stride * (size.height() - 1) + 4L * size.width();
    }

    /// Requires room for an image of `size`, before anything is written.
    void requireRoomFor(PhysicalSize size) {
        if (stride < 4L * size.width()) {
            throw new IllegalArgumentException("a stride of " + stride + " bytes cannot hold a row of " + size.width()
                    + " pixels, which needs " + 4L * size.width());
        }
        var needed = bytesFor(size);
        if (pixels.remaining() < needed) {
            throw new IllegalArgumentException("a " + size.width() + "x" + size.height() + " image at a stride of "
                    + stride + " needs " + needed + " bytes, and the buffer has " + pixels.remaining());
        }
    }
}
