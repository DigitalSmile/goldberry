package io.github.digitalsmile.goldberry.media;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.ReadOnlyBufferException;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import io.github.digitalsmile.goldberry.media.codec.PixelFormat;
import io.github.digitalsmile.goldberry.media.codec.VideoFrame;

/// [VideoPlanes], the planes form of a [Picture] (`docs/gpu-plan.md`, D8): what
/// it accepts, and what it hands back. No FFmpeg.
@DisplayName("VideoPlanes")
class VideoPlanesTest {

    /// A direct buffer of `bytes`, every byte its own index.
    private static ByteBuffer counting(int bytes) {
        var buffer = ByteBuffer.allocateDirect(bytes);
        for (var i = 0; i < bytes; i++) {
            buffer.put(i, (byte) i);
        }
        return buffer;
    }

    /// A 4×2 NV12 picture at a stride of 8: a luma plane of two rows, a chroma
    /// plane of one row of two Cb/Cr pairs.
    private static VideoPlanes nv12() {
        return new VideoPlanes(
                PixelFormat.NV12,
                4,
                2,
                List.of(counting(16), counting(8)),
                List.of(8, 8),
                VideoFrame.ColorMatrix.BT601,
                false,
                123);
    }

    @Test
    @DisplayName("hands back its layout, colour and time, and each sample at its place")
    void samples() {
        var planes = nv12();
        assertEquals(PixelFormat.NV12, planes.format());
        assertEquals(4, planes.width());
        assertEquals(2, planes.height());
        assertEquals(8, planes.stride(0));
        assertEquals(VideoFrame.ColorMatrix.BT601, planes.matrix());
        assertFalse(planes.fullRange());
        assertEquals(123, planes.ptsNanos());
        // Luma (1, 1) is byte 8 + 1; chroma Cr of the second pair is byte 3.
        assertEquals(9, planes.sample(0, 1, 1));
        assertEquals(3, planes.sample(1, 3, 0));
        assertTrue(planes.toString().contains("NV12 4×2 BT601 limited"));
    }

    @Test
    @DisplayName("reads a 16-bit sample little-endian, as stored")
    void sixteenBit() {
        var luma = ByteBuffer.allocateDirect(8).order(ByteOrder.LITTLE_ENDIAN);
        luma.putShort(2, (short) (1023 << 6));
        var planes = new VideoPlanes(
                PixelFormat.P010,
                2,
                2,
                List.of(luma, ByteBuffer.allocateDirect(4)),
                List.of(4, 4),
                VideoFrame.ColorMatrix.BT2020,
                true,
                0);
        assertEquals(1023 << 6, planes.sample(0, 1, 0));
    }

    @Test
    @DisplayName("hands out read-only planes, a fresh view each time")
    void planesAreReadOnly() {
        var planes = nv12();
        var plane = planes.plane(0);
        assertTrue(plane.isReadOnly());
        assertEquals(ByteOrder.LITTLE_ENDIAN, plane.order());
        plane.position(5);
        assertEquals(0, planes.plane(0).position(), "a caller's position is its own");
        assertThrows(ReadOnlyBufferException.class, () -> plane.put(0, (byte) 1));
    }

    @Test
    @DisplayName("refuses a sample outside the plane, rows past the chroma's among them")
    void samplesOutside() {
        var planes = nv12();
        assertThrows(IndexOutOfBoundsException.class, () -> planes.sample(0, 4, 0));
        assertThrows(IndexOutOfBoundsException.class, () -> planes.sample(1, 0, 1));
        assertThrows(IndexOutOfBoundsException.class, () -> planes.sample(0, -1, 0));
        assertThrows(IndexOutOfBoundsException.class, () -> planes.sample(2, 0, 0));
        assertThrows(IndexOutOfBoundsException.class, () -> planes.stride(2));
    }

    @Test
    @DisplayName("refuses planes that cannot hold the picture")
    void validates() {
        var matrix = VideoFrame.ColorMatrix.BT709;
        assertThrows(
                IllegalArgumentException.class,
                () -> new VideoPlanes(
                        PixelFormat.NV12, 0, 2, List.of(counting(16), counting(8)), List.of(8, 8), matrix, false, 0),
                "no size");
        assertThrows(
                IllegalArgumentException.class,
                () -> new VideoPlanes(
                        PixelFormat.I420, 4, 2, List.of(counting(16), counting(8)), List.of(8, 8), matrix, false, 0),
                "I420 has three planes");
        assertThrows(
                IllegalArgumentException.class,
                () -> new VideoPlanes(
                        PixelFormat.NV12, 4, 2, List.of(counting(16), counting(8)), List.of(3, 8), matrix, false, 0),
                "a stride shorter than a row");
        assertThrows(
                IllegalArgumentException.class,
                () -> new VideoPlanes(
                        PixelFormat.NV12, 4, 2, List.of(counting(11), counting(8)), List.of(8, 8), matrix, false, 0),
                "a luma plane one byte short");
        assertThrows(
                IllegalArgumentException.class,
                () -> new VideoPlanes(
                        PixelFormat.NV12,
                        4,
                        2,
                        List.of(ByteBuffer.allocate(16), counting(8)),
                        List.of(8, 8),
                        matrix,
                        false,
                        0),
                "a plane on the heap");
    }
}
