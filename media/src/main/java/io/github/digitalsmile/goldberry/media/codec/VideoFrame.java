package io.github.digitalsmile.goldberry.media.codec;

import java.lang.foreign.MemorySegment;
import java.util.List;
import java.util.Objects;

/// One decoded picture: NV12, I420 or P010 planes (`docs/goldberry-media.md` §5).
///
/// Part of the SPI from phase 2 so that a provider can be written against the
/// whole contract. The Engine presents these from phase 3.
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
    }
}
