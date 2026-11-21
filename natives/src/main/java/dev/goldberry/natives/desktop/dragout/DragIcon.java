package dev.goldberry.natives.desktop.dragout;

import java.nio.ByteBuffer;
import java.util.Objects;

/// The picture that follows the pointer during a drag, as a platform takes it.
///
/// @param width    in physical pixels
/// @param height   in physical pixels
/// @param stride   bytes from one row to the next
/// @param pixels   premultiplied BGRA, the platform's own order on a
///                 little-endian machine (`WL_SHM_FORMAT_ARGB8888`)
/// @param hotspotX where the pointer is in the picture, physical pixels from
///                 its left edge
/// @param hotspotY the same, from its top edge
/// @param scale    how many physical pixels make one logical one, an integer
///                 because a Wayland buffer's scale is; 1 where the window's
///                 is fractional
public record DragIcon(int width, int height, int stride, ByteBuffer pixels, int hotspotX, int hotspotY, int scale) {

    public DragIcon {
        Objects.requireNonNull(pixels, "pixels");
        if (width <= 0 || height <= 0) {
            throw new IllegalArgumentException("a drag icon is at least one pixel: " + width + "x" + height);
        }
        if (stride < width * 4L) {
            throw new IllegalArgumentException("a stride of " + stride + " is too short for " + width + " pixels");
        }
        if ((long) stride * height > pixels.remaining()) {
            throw new IllegalArgumentException("a " + width + "x" + height + " icon needs " + (long) stride * height
                    + " bytes, not " + pixels.remaining());
        }
        if (scale < 1) {
            throw new IllegalArgumentException("scale must be at least 1: " + scale);
        }
    }

    /// The pixels, row after row at [#stride], as one array.
    public byte[] bytes() {
        var copy = new byte[stride * height];
        pixels.duplicate().get(copy);
        return copy;
    }

    /// Whether [#scale] divides both sides, as a Wayland buffer's scale must.
    public boolean scaleDividesSize() {
        return width % scale == 0 && height % scale == 0;
    }
}
