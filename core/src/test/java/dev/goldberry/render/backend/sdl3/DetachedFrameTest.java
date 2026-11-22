package dev.goldberry.render.backend.sdl3;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;

import java.nio.ByteBuffer;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import dev.goldberry.render.PixelBuffer;
import dev.goldberry.render.model.PhysicalSize;
import dev.goldberry.render.model.PixelFormat;

/// The copy a composited window makes of its frame before it leaves the GPU
/// for good: the frame may be the compositor's own, whose memory goes with
/// it, so what the CPU then presents is a copy, packed whatever the frame's
/// stride was.
@DisplayName("a frame detached from the compositor's memory")
class DetachedFrameTest {

    @Test
    @DisplayName("holds every pixel of a frame with padded rows, in memory of its own, packed")
    void copiesPaddedRows() {
        var size = new PhysicalSize(5, 3);
        var stride = 32;
        var frame = new PixelBuffer(
                size, PixelFormat.BGRA32_PREMULTIPLIED, stride, ByteBuffer.allocateDirect(stride * size.height()));
        for (var y = 0; y < size.height(); y++) {
            for (var x = 0; x < stride / 4; x++) {
                frame.pixels().putInt(y * stride + x * 4, x < size.width() ? 0xFF000000 | y << 8 | x : 0xDEADBEEF);
            }
        }

        var copy = Sdl3Window.detached(frame);

        assertNotSame(frame.pixels(), copy.pixels());
        assertEquals(size, copy.size());
        assertEquals(20, copy.stride(), "packed");
        for (var y = 0; y < size.height(); y++) {
            for (var x = 0; x < size.width(); x++) {
                assertEquals(0xFF000000 | y << 8 | x, copy.pixels().getInt(y * 20 + x * 4), "at " + x + "," + y);
            }
        }
        frame.pixels().putInt(0, 0);
        assertEquals(0xFF000000, copy.pixels().getInt(0), "a copy, not a view");
    }
}
