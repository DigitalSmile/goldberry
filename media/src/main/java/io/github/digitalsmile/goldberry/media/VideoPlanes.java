package io.github.digitalsmile.goldberry.media;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import io.github.digitalsmile.goldberry.media.codec.PixelFormat;
import io.github.digitalsmile.goldberry.media.codec.VideoFrame;

/// One decoded picture as its Y'CbCr planes: the [PictureForm#PLANES] form of a
/// [Picture], which GPU present uploads and converts in a shader
/// (`docs/gpu-plan.md`, phase 6 and D8).
///
/// The planes are the decoder's, copied as they were decoded, in one of the
/// frame contract's four layouts ([PixelFormat]), and tagged with the matrix and
/// range the picture was encoded with. Nothing has been converted, scaled or
/// rounded, so a shader that converts them is compared with swscale on the same
/// bytes CPU present converts.
///
/// **Borrowed**, like a [VideoPicture]. The planes live in a buffer the Engine
/// reuses. A picture handed out by [MediaPlayer#shownPicture()] keeps its planes
/// until two more pictures have been handed out after it. A caller that keeps a
/// picture longer copies it.
///
/// Each plane is read-only and little-endian, which is the byte order of the
/// 16-bit samples of [PixelFormat#P010] and [PixelFormat#I010].
public final class VideoPlanes implements Picture {

    private final PixelFormat format;
    private final int width;
    private final int height;
    private final List<ByteBuffer> planes;
    private final int[] strides;
    private final VideoFrame.ColorMatrix matrix;
    private final boolean fullRange;
    private final long ptsNanos;

    /// A picture over `planes`.
    ///
    /// @param format    the plane layout
    /// @param width     width in pixels
    /// @param height    height in pixels
    /// @param planes    direct buffers, [PixelFormat#planes] of them, each read
    ///                  from position zero; the picture keeps read-only views
    /// @param strides   bytes from one row of each plane to the next
    /// @param matrix    the Y'CbCr → RGB matrix the picture was encoded with
    /// @param fullRange whether luma spans the whole code range (JPEG range)
    ///                  rather than the limited one
    /// @param ptsNanos  when the picture is presented, in nanoseconds of stream
    ///                  time
    /// @throws IllegalArgumentException when the size is not positive, the
    ///                                  planes or strides are not as many as
    ///                                  `format` has, a stride is shorter than
    ///                                  its plane's row, or a plane is on the
    ///                                  heap or too small for its rows
    public VideoPlanes(
            PixelFormat format,
            int width,
            int height,
            List<ByteBuffer> planes,
            List<Integer> strides,
            VideoFrame.ColorMatrix matrix,
            boolean fullRange,
            long ptsNanos) {
        Objects.requireNonNull(format, "format");
        Objects.requireNonNull(planes, "planes");
        Objects.requireNonNull(strides, "strides");
        Objects.requireNonNull(matrix, "matrix");
        if (width <= 0 || height <= 0) {
            throw new IllegalArgumentException("size " + width + "×" + height);
        }
        if (planes.size() != format.planes() || strides.size() != format.planes()) {
            throw new IllegalArgumentException(format + " has " + format.planes() + " planes, not " + planes.size()
                    + " planes and " + strides.size() + " strides");
        }
        var views = new ArrayList<ByteBuffer>(planes.size());
        this.strides = new int[planes.size()];
        for (var index = 0; index < planes.size(); index++) {
            var plane = Objects.requireNonNull(planes.get(index), "plane");
            int stride = strides.get(index);
            var rowBytes = format.planeRowBytes(index, width);
            if (stride < rowBytes) {
                throw new IllegalArgumentException("plane " + index + " has a stride of " + stride + ", less than its "
                        + rowBytes + " bytes a row");
            }
            var needed = (long) stride * (format.planeRows(index, height) - 1) + rowBytes;
            if (!plane.isDirect() || plane.capacity() < needed) {
                throw new IllegalArgumentException("plane " + index + " of a " + width + "×" + height + " " + format
                        + " picture needs a direct buffer of " + needed + " bytes");
            }
            views.add(plane.asReadOnlyBuffer().order(ByteOrder.LITTLE_ENDIAN));
            this.strides[index] = stride;
        }
        this.format = format;
        this.width = width;
        this.height = height;
        this.planes = List.copyOf(views);
        this.matrix = matrix;
        this.fullRange = fullRange;
        this.ptsNanos = ptsNanos;
    }

    /// The plane layout.
    public PixelFormat format() {
        return format;
    }

    @Override
    public int width() {
        return width;
    }

    @Override
    public int height() {
        return height;
    }

    /// Plane `index`, read-only and little-endian, from position zero. A fresh
    /// view each call, so a caller may move its position.
    ///
    /// @throws IndexOutOfBoundsException for a plane [#format()] does not have
    public ByteBuffer plane(int index) {
        return planes.get(index).duplicate().order(ByteOrder.LITTLE_ENDIAN).clear();
    }

    /// Bytes from one row of plane `index` to the next.
    ///
    /// @throws IndexOutOfBoundsException for a plane [#format()] does not have
    public int stride(int index) {
        Objects.checkIndex(index, strides.length);
        return strides[index];
    }

    /// The Y'CbCr → RGB matrix the picture was encoded with.
    public VideoFrame.ColorMatrix matrix() {
        return matrix;
    }

    /// Whether luma spans the whole code range rather than the limited one.
    public boolean fullRange() {
        return fullRange;
    }

    @Override
    public long ptsNanos() {
        return ptsNanos;
    }

    /// One stored sample of plane `index`, unsigned: what a test compares.
    /// `column` counts samples along the row, so in the interleaved chroma plane
    /// of [PixelFormat#NV12] and [PixelFormat#P010], Cb is at `2 × x` and Cr at
    /// `2 × x + 1`. A 16-bit sample is returned as stored, with its 10
    /// significant bits where its format keeps them.
    ///
    /// @throws IndexOutOfBoundsException outside the plane
    public int sample(int index, int column, int row) {
        var bytes = format.bytesPerSample();
        var rowBytes = format.planeRowBytes(index, width);
        if (column < 0 || row < 0 || column * bytes >= rowBytes || row >= format.planeRows(index, height)) {
            throw new IndexOutOfBoundsException("(" + column + ", " + row + ") is outside plane " + index + " of a "
                    + width + "×" + height + " " + format + " picture");
        }
        var plane = planes.get(index);
        var at = row * strides[index] + column * bytes;
        return bytes == 1 ? Byte.toUnsignedInt(plane.get(at)) : Short.toUnsignedInt(plane.getShort(at));
    }

    @Override
    public String toString() {
        return "VideoPlanes[" + format + " " + width + "×" + height + " " + matrix + (fullRange ? " full" : " limited")
                + " at " + ptsNanos + " ns]";
    }
}
