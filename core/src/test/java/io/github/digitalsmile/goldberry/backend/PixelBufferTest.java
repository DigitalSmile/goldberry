package io.github.digitalsmile.goldberry.backend;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.ByteBuffer;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class PixelBufferTest {

    private static final PixelFormat FORMAT = PixelFormat.BGRA32_PREMULTIPLIED;

    @Test
    @DisplayName("a tightly packed buffer is accepted")
    void acceptsTightlyPacked() {
        var buffer = PixelBuffer.allocate(new PhysicalSize(64, 32), FORMAT);

        assertTrue(buffer.isTightlyPacked());
        assertEquals(64 * 4, buffer.stride());
    }

    @Test
    @DisplayName("a padded stride is accepted, and is not tightly packed")
    void acceptsPaddedStride() {
        var size = new PhysicalSize(10, 4);
        var stride = 64;

        var buffer = new PixelBuffer(size, FORMAT, stride, ByteBuffer.allocate(stride * 4));

        assertFalse(buffer.isTightlyPacked());
    }

    @Test
    @DisplayName("the last row needs no trailing padding")
    void lastRowNeedsNoPadding() {
        // Blend2D hands over exactly (height-1)*stride + width*bpp bytes for a
        // tightly allocated image. Demanding height*stride would reject it, and
        // the resulting "buffer too small" would be Goldberry's bug, not
        // Blend2D's.
        var size = new PhysicalSize(10, 4);
        var stride = 64;
        var exact = (size.height() - 1) * stride + FORMAT.minimumStride(size.width());

        var buffer = new PixelBuffer(size, FORMAT, stride, ByteBuffer.allocate(exact));

        assertEquals(exact, buffer.pixels().remaining());
    }

    @Test
    @DisplayName("a stride narrower than one row is rejected")
    void rejectsTooSmallStride() {
        var size = new PhysicalSize(10, 4);

        var thrown = assertThrows(
                IllegalArgumentException.class,
                () -> new PixelBuffer(size, FORMAT, 39, ByteBuffer.allocate(1000)));

        assertTrue(thrown.getMessage().contains("stride"), thrown::getMessage);
    }

    @Test
    @DisplayName("a buffer too small for the size it claims is rejected")
    void rejectsTooSmallBuffer() {
        var size = new PhysicalSize(100, 100);

        var thrown = assertThrows(
                IllegalArgumentException.class,
                () -> new PixelBuffer(size, FORMAT, 400, ByteBuffer.allocate(400)));

        assertTrue(thrown.getMessage().contains("bytes"), thrown::getMessage);
    }

    @Test
    @DisplayName("a zero-height buffer needs no bytes")
    void zeroHeightNeedsNothing() {
        var buffer = new PixelBuffer(new PhysicalSize(10, 0), FORMAT, 40, ByteBuffer.allocate(0));

        assertEquals(0, buffer.pixels().remaining());
    }

    @Test
    @DisplayName("a read-only view keeps the same geometry")
    void readOnlyKeepsGeometry() {
        var buffer = PixelBuffer.allocate(new PhysicalSize(8, 8), FORMAT);

        var readOnly = buffer.asReadOnly();

        assertEquals(buffer.size(), readOnly.size());
        assertEquals(buffer.stride(), readOnly.stride());
        assertTrue(readOnly.pixels().isReadOnly());
    }

    @Test
    @DisplayName("the format knows its own stride arithmetic")
    void formatComputesStride() {
        assertEquals(4, FORMAT.bytesPerPixel());
        assertEquals(4000, FORMAT.minimumStride(1000));
    }

    @Test
    @DisplayName("a stride that would overflow is rejected rather than wrapped")
    void rejectsStrideOverflow() {
        assertThrows(ArithmeticException.class, () -> FORMAT.minimumStride(Integer.MAX_VALUE));
    }
}
