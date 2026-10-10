package dev.goldberry.media.picture;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Objects;

import dev.goldberry.media.MediaPlayer;

/// One decoded picture, ready to draw: premultiplied BGRA at the size it was
/// decoded at, what a view that blits on the CPU draws. Opaque, unless the video
/// carries alpha ([#opaque()]), as a WebM sticker does: then a view blends it over
/// what is beneath it.
///
/// **Borrowed.** The pixels live in a buffer the Engine reuses. A picture handed
/// out by [MediaPlayer#currentPicture()] keeps its pixels until two more pictures
/// have been handed out after it, which is enough for a view to draw the picture
/// it asked for in the frame it asked in, even with a second view on the same
/// player. A caller that keeps a picture longer copies it.
///
/// The [PictureForm#CONVERTED] form of a [Picture]; [VideoPlanes] is the other.
///
/// The format is the toolkit's own, `0xAARRGGBB` in memory on a little-endian
/// machine, so a widget wraps the buffer and blits it with no conversion.
///
/// Read more: [`video-view`](https://goldberry.dev/docs/components/media.html#video-view).
public final class VideoPicture implements Picture {

    private final int width;
    private final int height;
    private final int stride;
    private final ByteBuffer pixels;
    private final long ptsNanos;
    private final boolean opaque;

    /// An opaque picture over `pixels`.
    ///
    /// @param width    width in pixels
    /// @param height   height in pixels
    /// @param stride   bytes from one row to the next; at least `4 × width`
    /// @param pixels   a direct buffer of at least `stride × height` bytes,
    ///                 which the picture keeps a read-only view of
    /// @param ptsNanos when the picture is presented, in nanoseconds of stream time
    public VideoPicture(int width, int height, int stride, ByteBuffer pixels, long ptsNanos) {
        this(width, height, stride, pixels, ptsNanos, true);
    }

    /// A picture over `pixels`, opaque or with alpha.
    ///
    /// @param width    width in pixels
    /// @param height   height in pixels
    /// @param stride   bytes from one row to the next; at least `4 × width`
    /// @param pixels   a direct buffer of at least `stride × height` bytes,
    ///                 which the picture keeps a read-only view of
    /// @param ptsNanos when the picture is presented, in nanoseconds of stream time
    /// @param opaque   whether every pixel's alpha is 255; false for a picture
    ///                 with alpha, whose colour is premultiplied by it
    public VideoPicture(int width, int height, int stride, ByteBuffer pixels, long ptsNanos, boolean opaque) {
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
        this.opaque = opaque;
    }

    /// Whether every pixel is opaque. A picture that is not came from a video
    /// with alpha, and is drawn over what is beneath it rather than in its place.
    public boolean opaque() {
        return opaque;
    }

    /// Width in pixels.
    @Override
    public int width() {
        return width;
    }

    /// Height in pixels.
    @Override
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
    @Override
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
