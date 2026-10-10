package dev.goldberry.media.view.gpu;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import dev.goldberry.gpu.video.ColorMatrix;
import dev.goldberry.gpu.video.PlaneLayout;
import dev.goldberry.gpu.video.VideoImage;
import dev.goldberry.media.codec.PixelFormat;
import dev.goldberry.media.codec.VideoFrame;
import dev.goldberry.media.picture.VideoPicture;
import dev.goldberry.media.picture.VideoPlanes;

/// [Pictures]: a picture in the video layer's words. Every pixel format and
/// every matrix has its counterpart, and the image borrows the picture's bytes.
/// Skipped in the run without `:gpu`, where the layer's types are not there.
@DisplayName("a picture as the GPU's video image")
class PicturesTest {

    @BeforeEach
    void needsGpuModule() {
        assumeTrue(GpuVideo.available());
    }

    private static VideoPlanes planes(PixelFormat format, VideoFrame.ColorMatrix matrix, boolean fullRange) {
        var buffers = new ArrayList<ByteBuffer>();
        var strides = new ArrayList<Integer>();
        for (var plane = 0; plane < format.planes(); plane++) {
            buffers.add(ByteBuffer.allocateDirect(64));
            strides.add(format.planeRowBytes(plane, 4));
        }
        return new VideoPlanes(format, 4, 2, buffers, strides, matrix, fullRange, 0);
    }

    @Test
    @DisplayName("every opaque pixel format has a layout of the same name, and the one with alpha has none")
    void everyPixelFormat() {
        var opaque = 0;
        for (var format : PixelFormat.values()) {
            if (format.hasAlpha()) {
                assertThrows(IllegalArgumentException.class, () -> Pictures.layout(format), format.name());
                continue;
            }
            opaque++;
            assertEquals(PlaneLayout.valueOf(format.name()), Pictures.layout(format), format.name());
        }
        assertEquals(opaque, PlaneLayout.values().length, "and no layout is left over");
    }

    @Test
    @DisplayName("every colour matrix has a matrix of the same name")
    void everyMatrix() {
        for (var matrix : VideoFrame.ColorMatrix.values()) {
            assertEquals(ColorMatrix.valueOf(matrix.name()), Pictures.matrix(matrix), matrix.name());
        }
        assertEquals(VideoFrame.ColorMatrix.values().length, ColorMatrix.values().length, "and none is left over");
    }

    @Test
    @DisplayName("maps planes to the layer's vocabulary, layout, colour and all")
    void mapsPlanes() {
        for (var format : PixelFormat.values()) {
            if (format.hasAlpha()) {
                // Never handed out as planes: the queue converts it.
                continue;
            }
            var picture = planes(format, VideoFrame.ColorMatrix.BT709, false);
            var image = assertInstanceOf(VideoImage.Planes.class, Pictures.image(picture));
            assertEquals(PlaneLayout.valueOf(format.name()), image.layout());
            assertEquals(ColorMatrix.BT709, image.matrix());
            var strides = new ArrayList<Integer>();
            for (var plane = 0; plane < format.planes(); plane++) {
                strides.add(picture.stride(plane));
            }
            assertEquals(strides, image.strides());
            assertEquals(format.planes(), image.planes().size());
            assertEquals(4, image.width());
            assertEquals(2, image.height());
            assertFalse(image.fullRange());
        }
        var full = (VideoImage.Planes) Pictures.image(planes(PixelFormat.NV12, VideoFrame.ColorMatrix.BT2020, true));
        assertEquals(ColorMatrix.BT2020, full.matrix());
        assertTrue(full.fullRange());
    }

    @Test
    @DisplayName("borrows the picture's planes rather than copying them")
    void borrowsPlanes() {
        var decoded = List.of(ByteBuffer.allocateDirect(8), ByteBuffer.allocateDirect(2), ByteBuffer.allocateDirect(2));
        var picture = new VideoPlanes(
                PixelFormat.I420, 4, 2, decoded, List.of(4, 2, 2), VideoFrame.ColorMatrix.BT601, false, 0);
        var image = (VideoImage.Planes) Pictures.image(picture);
        for (var plane = 0; plane < 3; plane++) {
            decoded.get(plane).put(0, (byte) (40 + plane));
            assertEquals(40 + plane, image.planes().get(plane).get(0), "plane " + plane + " is the same memory");
        }
    }

    @Test
    @DisplayName("maps a converted picture to BGRA over its own pixels")
    void mapsBgra() {
        var pixels = ByteBuffer.allocateDirect(32);
        var picture = new VideoPicture(4, 2, 16, pixels, 0);
        var image = assertInstanceOf(VideoImage.Bgra.class, Pictures.image(picture));
        assertEquals(16, image.stride());
        assertEquals(4, image.width());
        assertEquals(2, image.height());
        pixels.put(5, (byte) 77);
        assertEquals(77, image.pixels().get(5), "the same memory");
    }

    @Test
    @DisplayName("makes a new image for every call: the identity is the caller's to keep")
    void newImageEachCall() {
        var picture = planes(PixelFormat.NV12, VideoFrame.ColorMatrix.BT709, false);
        assertNotSame(Pictures.image(picture), Pictures.image(picture));
    }
}
