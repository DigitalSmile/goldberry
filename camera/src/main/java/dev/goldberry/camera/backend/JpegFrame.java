package dev.goldberry.camera.backend;

import java.nio.ByteBuffer;
import java.util.Objects;

/// One Motion JPEG frame a camera lends: a whole JPEG, possibly without its
/// Huffman tables, which many USB cameras leave out.
///
/// @param bytes          the frame, from its position to its limit, read-only
///                       and valid during the call it is lent to
/// @param width          pixels across, as the mode says
/// @param height         pixels down, as the mode says
/// @param timestampNanos when the camera took it, in the system's clock, or
///                       zero where it does not say
public record JpegFrame(ByteBuffer bytes, int width, int height, long timestampNanos) implements LentFrame {

    public JpegFrame {
        Objects.requireNonNull(bytes, "bytes");
        if (width <= 0 || height <= 0) {
            throw new IllegalArgumentException("size " + width + "×" + height);
        }
    }
}
