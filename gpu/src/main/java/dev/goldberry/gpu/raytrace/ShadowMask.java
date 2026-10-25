package dev.goldberry.gpu.raytrace;

import java.nio.ByteBuffer;
import java.util.Objects;

/// Where the light reaches, a byte a pixel, rows from the top with no padding:
/// `0xFF` where it does, or where the pixel's ray hit nothing, and 0 in shadow.
/// Laid out for an upload into an `R8_UNORM` texture, which a renderer's
/// shader then samples as 1 and 0.
///
/// What [RayTracer#traceShadows] packs on the GPU, and what
/// [Hits#shadowMask()] makes from the records.
public final class ShadowMask {

    /// A pixel the light reaches.
    public static final byte LIT = (byte) 0xFF;

    /// A pixel in shadow.
    public static final byte SHADOWED = 0;

    private final int width;
    private final int height;
    private final ByteBuffer pixels;

    private ShadowMask(int width, int height, ByteBuffer pixels) {
        this.width = width;
        this.height = height;
        this.pixels = pixels;
    }

    /// The mask of a `width` by `height` picture in `pixels`, laid out as
    /// above, which become this object's.
    ///
    /// @throws IllegalArgumentException when `pixels` is not the picture's size
    public static ShadowMask of(int width, int height, ByteBuffer pixels) {
        Objects.requireNonNull(pixels, "pixels");
        if (width < 1 || height < 1) {
            throw new IllegalArgumentException("a picture of " + width + "×" + height);
        }
        var expected = (long) width * height;
        if (pixels.remaining() != expected) {
            throw new IllegalArgumentException(
                    width + "×" + height + " is a mask of " + expected + " bytes, not " + pixels.remaining());
        }
        return new ShadowMask(width, height, pixels.slice());
    }

    /// The picture's width.
    public int width() {
        return width;
    }

    /// The picture's height.
    public int height() {
        return height;
    }

    /// Whether the light reaches pixel `x`, `y`: true where its ray hit
    /// nothing.
    public boolean isLit(int x, int y) {
        Objects.checkIndex(x, width);
        Objects.checkIndex(y, height);
        return pixels.get(y * width + x) != SHADOWED;
    }

    /// How many pixels are in shadow.
    public int shadowedCount() {
        var count = 0;
        for (var i = 0; i < pixels.limit(); i++) {
            if (pixels.get(i) == SHADOWED) {
                count++;
            }
        }
        return count;
    }

    /// The pixels as they are laid out above: a read-only view, for a
    /// [dev.goldberry.gpu.CopyPass] upload.
    public ByteBuffer pixels() {
        return pixels.asReadOnlyBuffer();
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof ShadowMask mask
                && mask.width == width
                && mask.height == height
                && mask.pixels.equals(pixels);
    }

    @Override
    public int hashCode() {
        return Objects.hash(width, height, pixels);
    }

    @Override
    public String toString() {
        return "ShadowMask[" + width + "×" + height + ", " + shadowedCount() + " in shadow]";
    }
}
