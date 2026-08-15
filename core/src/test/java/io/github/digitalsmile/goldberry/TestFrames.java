package io.github.digitalsmile.goldberry;

import io.github.digitalsmile.goldberry.backend.DisplayScale;
import io.github.digitalsmile.goldberry.backend.PhysicalSize;
import io.github.digitalsmile.goldberry.backend.PixelBuffer;
import io.github.digitalsmile.goldberry.backend.PixelFormat;
import java.nio.ByteOrder;

/// A [Frame] over a buffer a test can read back.
///
/// `Frame`'s constructor is package-private on purpose — an application gets one
/// from `onPaint` and never builds one — so this lives in `Frame`'s own package
/// and hands the pair out to tests that do not. Widening the constructor to
/// public would put a rendering-internals type in the toolkit's API for the sake
/// of a test helper.
public final class TestFrames {

    private TestFrames() {
    }

    /// A frame of `width` x `height` **physical** pixels at `scale`.
    public record Target(Frame frame, PixelBuffer buffer) {

        /// The pixel at `(x, y)` in physical coordinates, as `0xAARRGGBB`.
        public int pixel(int x, int y) {
            var pixels = buffer.pixels().duplicate().order(ByteOrder.LITTLE_ENDIAN);
            return pixels.getInt(y * buffer.stride() + x * 4);
        }

        /// The alpha of the pixel at `(x, y)`, which is how a test asks whether
        /// anything was painted there at all.
        public int alphaAt(int x, int y) {
            return pixel(x, y) >>> 24;
        }
    }

    public static Target of(int width, int height, float scale) {
        var buffer = PixelBuffer.allocate(
                new PhysicalSize(width, height), PixelFormat.BGRA32_PREMULTIPLIED);
        return new Target(new Frame(buffer, new DisplayScale(scale)), buffer);
    }
}
