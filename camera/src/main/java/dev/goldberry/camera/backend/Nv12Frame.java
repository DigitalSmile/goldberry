package dev.goldberry.camera.backend;

import java.nio.ByteBuffer;
import java.util.Objects;

import dev.goldberry.media.codec.VideoFrame;

/// One NV12 frame a camera lends: luma rows, then interleaved chroma rows at
/// half the size, in one buffer.
///
/// @param bytes          the frame's memory, read-only, valid during the call
///                       it is lent to
/// @param width          pixels across
/// @param height         pixels down
/// @param lumaOffset     where the luma plane starts in `bytes`
/// @param lumaStride     bytes from one luma row to the next
/// @param chromaOffset   where the chroma plane starts
/// @param chromaStride   bytes from one chroma row to the next
/// @param matrix         the Y'CbCr matrix the frame is in
/// @param fullRange      whether luma spans the whole code range
/// @param timestampNanos when the camera took it, in the system's clock, or
///                       zero where it does not say
public record Nv12Frame(
        ByteBuffer bytes,
        int width,
        int height,
        int lumaOffset,
        int lumaStride,
        int chromaOffset,
        int chromaStride,
        VideoFrame.ColorMatrix matrix,
        boolean fullRange,
        long timestampNanos)
        implements LentFrame {

    public Nv12Frame {
        Objects.requireNonNull(bytes, "bytes");
        Objects.requireNonNull(matrix, "matrix");
        if (width <= 0 || height <= 0) {
            throw new IllegalArgumentException("size " + width + "×" + height);
        }
        var chromaRow = (width + 1) / 2 * 2;
        if (lumaStride < width || chromaStride < chromaRow) {
            throw new IllegalArgumentException("strides " + lumaStride + " and " + chromaStride + " for " + width);
        }
        var luma = lumaOffset + (long) lumaStride * (height - 1) + width;
        var chroma = chromaOffset + (long) chromaStride * ((height + 1) / 2 - 1) + chromaRow;
        if (lumaOffset < 0 || chromaOffset < 0 || Math.max(luma, chroma) > bytes.limit()) {
            throw new IllegalArgumentException(
                    "a " + width + "×" + height + " frame does not fit in " + bytes.limit() + " bytes");
        }
    }
}
