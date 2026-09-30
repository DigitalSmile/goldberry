package io.github.digitalsmile.goldberry.media.platform.windows;

import static java.lang.foreign.MemoryLayout.PathElement.groupElement;
import static java.lang.foreign.ValueLayout.JAVA_INT;
import static java.lang.foreign.ValueLayout.JAVA_INT_UNALIGNED;
import static java.lang.foreign.ValueLayout.JAVA_SHORT;
import static java.lang.foreign.ValueLayout.JAVA_SHORT_UNALIGNED;

import java.lang.foreign.MemoryLayout;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.StructLayout;
import java.nio.ByteOrder;
import java.util.Optional;
import java.util.OptionalInt;

import io.github.digitalsmile.goldberry.media.codec.VideoFrame;

/// Where a decoded NV12 or P010 picture's planes are in an MFT's output buffer,
/// which part of it is shown, and its colour: the arithmetic the video decoder
/// does on what Media Foundation reports, kept apart so a test can check it
/// without Windows.
///
/// Both layouts are two planes in one buffer: the luma rows, then the
/// interleaved chroma rows, half as many, at the same stride. The chroma plane
/// starts after as many luma rows as the buffer was allocated for, which is the
/// frame's height (`MF_MT_FRAME_SIZE`, usually a multiple of 16), not the
/// picture's.
final class PictureLayout {

    /// `MFOffset` (`mfobjects.h`): `{ WORD fract; short value; }`, a fixed-point
    /// coordinate.
    static final StructLayout OFFSET =
            MemoryLayout.structLayout(JAVA_SHORT.withName("fract"), JAVA_SHORT.withName("value"));

    /// `MFVideoArea` (`mfobjects.h`): `{ MFOffset OffsetX; MFOffset OffsetY; SIZE Area; }`,
    /// `SIZE` being `{ LONG cx; LONG cy; }`: 16 bytes.
    static final StructLayout VIDEO_AREA = MemoryLayout.structLayout(
            OFFSET.withName("OffsetX"),
            OFFSET.withName("OffsetY"),
            MemoryLayout.structLayout(JAVA_INT.withName("cx"), JAVA_INT.withName("cy"))
                    .withName("Area"));

    private static final long OFFSET_X = VIDEO_AREA.byteOffset(groupElement("OffsetX"), groupElement("value"));
    private static final long OFFSET_Y = VIDEO_AREA.byteOffset(groupElement("OffsetY"), groupElement("value"));
    private static final long AREA_CX = VIDEO_AREA.byteOffset(groupElement("Area"), groupElement("cx"));
    private static final long AREA_CY = VIDEO_AREA.byteOffset(groupElement("Area"), groupElement("cy"));

    private PictureLayout() {}

    /// A rectangle of the picture, in pixels.
    record Area(int x, int y, int width, int height) {}

    /// The byte ranges of the two planes, from the start of the buffer.
    ///
    /// @param lumaOffset   where the first shown luma row starts
    /// @param lumaSize     the bytes from there to the end of the luma plane
    /// @param chromaOffset where the first shown chroma row starts
    /// @param chromaSize   the bytes from there to the end of the chroma plane
    record Planes(long lumaOffset, long lumaSize, long chromaOffset, long chromaSize) {

        /// The bytes of the buffer the planes need: the end of the chroma plane.
        long end() {
            return chromaOffset + chromaSize;
        }
    }

    /// The `MFVideoArea` blob of `MF_MT_MINIMUM_DISPLAY_APERTURE`, read: the
    /// offsets' whole parts and the size. Empty for a blob of another size.
    static Optional<Area> aperture(byte[] blob) {
        if (blob.length != VIDEO_AREA.byteSize()) {
            return Optional.empty();
        }
        var in = MemorySegment.ofArray(blob);
        var s16 = JAVA_SHORT_UNALIGNED.withOrder(ByteOrder.LITTLE_ENDIAN);
        var s32 = JAVA_INT_UNALIGNED.withOrder(ByteOrder.LITTLE_ENDIAN);
        return Optional.of(
                new Area(in.get(s16, OFFSET_X), in.get(s16, OFFSET_Y), in.get(s32, AREA_CX), in.get(s32, AREA_CY)));
    }

    /// `MF_MT_FRAME_SIZE`'s width: the high 32 bits.
    static int frameWidth(long frameSize) {
        return (int) (frameSize >>> 32);
    }

    /// `MF_MT_FRAME_SIZE`'s height: the low 32 bits.
    static int frameHeight(long frameSize) {
        return (int) frameSize;
    }

    /// `width` and `height` as `MF_MT_FRAME_SIZE` holds them.
    static long frameSize(int width, int height) {
        return (long) width << 32 | Integer.toUnsignedLong(height);
    }

    /// The part of the frame shown: the aperture where the output type has one
    /// and the frame otherwise, kept inside the frame, and no larger than the
    /// track's own size where it has one. The origin is rounded down to even, as
    /// 4:2:0 chroma can only be cropped by pairs of pixels.
    static Area visible(Optional<Area> aperture, int frameWidth, int frameHeight, int trackWidth, int trackHeight) {
        var area =
                aperture.filter(a -> a.width() > 0 && a.height() > 0).orElse(new Area(0, 0, frameWidth, frameHeight));
        var x = Math.clamp(area.x() & ~1, 0, Math.max(frameWidth - 1, 0));
        var y = Math.clamp(area.y() & ~1, 0, Math.max(frameHeight - 1, 0));
        var width = Math.min(area.width(), frameWidth - x);
        var height = Math.min(area.height(), frameHeight - y);
        if (trackWidth > 0) {
            width = Math.min(width, trackWidth);
        }
        if (trackHeight > 0) {
            height = Math.min(height, trackHeight);
        }
        return new Area(x, y, width, height);
    }

    /// The bytes from one row to the next: the pitch a 2D buffer locked with,
    /// else the output type's `MF_MT_DEFAULT_STRIDE`, else the frame's width
    /// packed.
    ///
    /// @throws IllegalStateException for a negative stride: a bottom-up picture,
    ///                               which a YUV decoder does not make
    static int stride(OptionalInt pitch, OptionalInt defaultStride, int frameWidth, int bytesPerSample) {
        var stride = pitch.isPresent() && pitch.getAsInt() != 0
                ? pitch.getAsInt()
                : defaultStride.orElse(0) != 0 ? defaultStride.getAsInt() : frameWidth * bytesPerSample;
        if (stride < 0) {
            throw new IllegalStateException("a bottom-up picture, stride " + stride);
        }
        return stride;
    }

    /// The luma rows the buffer was allocated for: the frame's height, or more
    /// when the buffer's length says so exactly, as it does for a decoder that
    /// pads the rows it allocates beyond the height it reports.
    ///
    /// @param length the bytes of data in the buffer, or a negative number when
    ///               it is not known (a 2D buffer)
    static int bufferRows(int frameHeight, int stride, long length) {
        if (length <= 0 || stride <= 0) {
            return frameHeight;
        }
        var rows = (int) (length * 2 / (3L * stride));
        if (rows > frameHeight && twoPlaneBytes(stride, rows) == length) {
            return rows;
        }
        return frameHeight;
    }

    /// The planes of the `visible` area in a buffer of `rows` luma rows at
    /// `stride`, each plane from its first shown row to its end.
    static Planes planes(Area visible, int stride, int rows, int bytesPerSample) {
        var lumaOffset = (long) visible.y() * stride + (long) visible.x() * bytesPerSample;
        var lumaEnd = (long) stride * rows;
        // A chroma sample pair covers two pixels: x/2 pairs of two samples each.
        var chromaOffset = lumaEnd + (long) (visible.y() / 2) * stride + (long) (visible.x() / 2) * 2 * bytesPerSample;
        var chromaEnd = twoPlaneBytes(stride, rows);
        return new Planes(lumaOffset, lumaEnd - lumaOffset, chromaOffset, chromaEnd - chromaOffset);
    }

    /// The bytes of a two-plane 4:2:0 picture of `rows` luma rows at `stride`.
    static long twoPlaneBytes(int stride, int rows) {
        return (long) stride * (rows + (rows + 1) / 2);
    }

    /// The matrix the output type names (`MF_MT_YUV_MATRIX`), or where it names
    /// none, what the decoders assume of an untagged picture: BT.709 from 720
    /// rows, BT.601 below.
    static VideoFrame.ColorMatrix matrix(OptionalInt transferMatrix, int height) {
        return switch (transferMatrix.orElse(MfGuids.MATRIX_UNKNOWN)) {
            // SMPTE 240M is within a rounding of BT.709, and FFmpeg's swscale
            // treats it so.
            case MfGuids.MATRIX_BT709, MfGuids.MATRIX_SMPTE240M -> VideoFrame.ColorMatrix.BT709;
            case MfGuids.MATRIX_BT601 -> VideoFrame.ColorMatrix.BT601;
            case MfGuids.MATRIX_BT2020_10, MfGuids.MATRIX_BT2020_12 -> VideoFrame.ColorMatrix.BT2020;
            default -> height >= 720 ? VideoFrame.ColorMatrix.BT709 : VideoFrame.ColorMatrix.BT601;
        };
    }

    /// Whether the output type says full range (`MF_MT_VIDEO_NOMINAL_RANGE`);
    /// limited, the default for video, where it says nothing else.
    static boolean fullRange(OptionalInt nominalRange) {
        return nominalRange.orElse(MfGuids.RANGE_UNKNOWN) == MfGuids.RANGE_0_255;
    }
}
