package io.github.digitalsmile.goldberry.media;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Objects;

/// One decoded picture, ready to draw: premultiplied BGRA, opaque, at the size it
/// was decoded at (`docs/goldberry-media.md` §3, "CPU present").
///
/// **Borrowed.** The pixels live in a buffer the Engine reuses. A picture handed
/// out by [MediaPlayer#currentPicture()] keeps its pixels until two more pictures
/// have been handed out after it, which is enough for a view to draw the picture
/// it asked for in the frame it asked in, even with a second view on the same
/// player. A caller that keeps a picture longer copies it.
///
/// The format is the toolkit's own, `0xAARRGGBB` in memory on a little-endian
/// machine, so a widget wraps the buffer and blits it with no conversion.
public final class VideoPicture {

    private final int width;
    private final int height;
    private final int stride;
    private final ByteBuffer pixels;
    private final long ptsNanos;

    /// A picture over `pixels`.
    ///
    /// @param width    width in pixels
    /// @param height   height in pixels
    /// @param stride   bytes from one row to the next; at least `4 × width`
    /// @param pixels   a direct buffer of at least `stride × height` bytes,
    ///                 which the picture keeps a read-only view of
    /// @param ptsNanos when the picture is presented, in nanoseconds of stream time
    public VideoPicture(int width, int height, int stride, ByteBuffer pixels, long ptsNanos) {
        Objects.requireNonNull(pixels, "pixels");
        if (width <= 0 || height <= 0) {
            throw new IllegalArgumentException("size " + width + "×" + height);
        }
        if (stride < width * 4) {
            throw new IllegalArgumentException("a stride of " + stride + " cannot hold " + width + " pixels");
        }
        if (!pixels.isDirect() || pixels.capacity() < (long) stride * height) {
            throw new IllegalArgumentException("a " + width + "×" + height + " picture needs a direct buffer of "
                    + (long) stride * height + " bytes");
        }
        this.width = width;
        this.height = height;
        this.stride = stride;
        // Little-endian, so an int read from it is `0xAARRGGBB`: the bytes are
        // B, G, R, A in memory.
        this.pixels = pixels.asReadOnlyBuffer().order(ByteOrder.LITTLE_ENDIAN);
        this.ptsNanos = ptsNanos;
    }

    /// Width in pixels.
    public int width() {
        return width;
    }

    /// Height in pixels.
    public int height() {
        return height;
    }

    /// Bytes from one row to the next.
    public int stride() {
        return stride;
    }

    /// The pixels, read-only and little-endian: premultiplied BGRA, `stride ×
    /// height` bytes from position zero. A fresh view each call, so a caller may
    /// move its position.
    public ByteBuffer pixels() {
        return pixels.duplicate().order(ByteOrder.LITTLE_ENDIAN).clear();
    }

    /// When the picture is presented, in nanoseconds of stream time.
    public long ptsNanos() {
        return ptsNanos;
    }

    /// One pixel as `0xAARRGGBB`: what a test compares.
    ///
    /// @throws IndexOutOfBoundsException outside the picture
    public int argb(int x, int y) {
        if (x < 0 || y < 0 || x >= width || y >= height) {
            throw new IndexOutOfBoundsException("(" + x + ", " + y + ") is outside " + width + "×" + height);
        }
        return pixels.getInt(y * stride + x * 4);
    }

    @Override
    public String toString() {
        return "VideoPicture[" + width + "×" + height + " at " + ptsNanos + " ns]";
    }
}
