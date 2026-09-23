package io.github.digitalsmile.goldberry.media.codec;

import java.lang.foreign.MemorySegment;
import java.util.List;
import java.util.Objects;

/// One decoded picture: NV12, I420, P010 or I010 planes (`docs/goldberry-media.md`
/// §5).
///
/// Borrowed, like every [Frame]: the planes are the decoder's until its next
/// call. The Engine converts the picture for presentation before it asks for the
/// next one, so a provider may hand out the same buffers every time.
///
/// Each plane must hold its rows at its stride: `stride × (rows − 1) + row bytes`
/// at least, where [PixelFormat#planeRows] and [PixelFormat#planeRowBytes] say
/// what the rows and row bytes are. A plane that is too small is refused here,
/// before anything native reads past its end. Planes are native memory, never a
/// heap array: the present path passes their addresses to swscale.
///
/// @param format    the plane layout
/// @param width     width in pixels
/// @param height    height in pixels
/// @param planes    the planes, [PixelFormat#planes] of them
/// @param strides   bytes from one row of each plane to the next
/// @param matrix    the YUV→RGB matrix the picture was encoded with
/// @param fullRange whether luma spans 0–255 (JPEG range) rather than 16–235
/// @param ptsNanos  when the picture is presented, or [Frame#NO_PTS]
public record VideoFrame(
        PixelFormat format,
        int width,
        int height,
        List<MemorySegment> planes,
        List<Integer> strides,
        ColorMatrix matrix,
        boolean fullRange,
        long ptsNanos)
        implements Frame {

    /// The YUV→RGB matrices the present path knows.
    public enum ColorMatrix {
        BT601,
        BT709,
        BT2020,
    }

    public VideoFrame {
        Objects.requireNonNull(format, "format");
        Objects.requireNonNull(matrix, "matrix");
        if (width <= 0 || height <= 0) {
            throw new IllegalArgumentException("size " + width + "×" + height);
        }
        planes = List.copyOf(planes);
        strides = List.copyOf(strides);
        if (planes.size() != format.planes() || strides.size() != format.planes()) {
            throw new IllegalArgumentException(format + " has " + format.planes() + " planes, not " + planes.size()
                    + " planes and " + strides.size() + " strides");
        }
        for (var plane = 0; plane < planes.size(); plane++) {
            if (!planes.get(plane).isNative()) {
                throw new IllegalArgumentException(
                        "plane " + plane
                                + " is on the Java heap; the present path hands planes to native code, so they are native memory");
            }
            var rowBytes = format.planeRowBytes(plane, width);
            var stride = strides.get(plane);
            if (stride < rowBytes) {
                throw new IllegalArgumentException("plane " + plane + " has a stride of " + stride + ", less than its "
                        + rowBytes + " bytes a row");
            }
            var needed = (long) stride * (format.planeRows(plane, height) - 1) + rowBytes;
            if (planes.get(plane).byteSize() < needed) {
                throw new IllegalArgumentException(
                        "plane " + plane + " of a " + width + "×" + height + " " + format + " picture needs " + needed
                                + " bytes, and has " + planes.get(plane).byteSize());
            }
        }
    }
}
