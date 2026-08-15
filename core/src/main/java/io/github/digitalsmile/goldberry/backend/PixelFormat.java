package io.github.digitalsmile.goldberry.backend;

/// The pixel layout of a [PixelBuffer].
///
/// One entry, deliberately. Blend2D rasterizes to `BL_FORMAT_PRGB32` and every
/// backend Goldberry ships can present that, so a second format would be a
/// conversion nobody asked for. The enum exists because `present()` taking an
/// untyped buffer is how a format mismatch becomes a screen of blue faces
/// instead of a type error.
public enum PixelFormat {

    /// 32 bits per pixel, blue-green-red-alpha in memory order on little-endian
    /// machines, with colour channels **premultiplied** by alpha.
    ///
    /// Blend2D's `BL_FORMAT_PRGB32`. Premultiplied is not a detail: compositing
    /// straight-alpha data as if it were premultiplied darkens every edge in the
    /// frame, which reads as "the antialiasing looks wrong" rather than as a
    /// format bug.
    BGRA32_PREMULTIPLIED(4);

    private final int bytesPerPixel;

    PixelFormat(int bytesPerPixel) {
        this.bytesPerPixel = bytesPerPixel;
    }

    public int bytesPerPixel() {
        return bytesPerPixel;
    }

    /// The tightest stride for a row of `width` pixels, in bytes. Real buffers
    /// may be padded wider; none may be narrower.
    public int minimumStride(int width) {
        return Math.multiplyExact(width, bytesPerPixel);
    }
}
