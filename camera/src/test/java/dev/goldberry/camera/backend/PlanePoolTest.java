package dev.goldberry.camera.backend;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.nio.ByteBuffer;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import dev.goldberry.media.codec.PixelFormat;
import dev.goldberry.media.codec.VideoFrame;

/// The three pictures' worth of memory a camera copies its frames into.
@DisplayName("a plane pool")
class PlanePoolTest {

    /// A 6×4 NV12 frame with a pitch of 8, each luma byte its column plus ten
    /// times its row, each chroma pair `(100 + column, 200 + row)`.
    private static Nv12Frame frame() {
        var bytes = ByteBuffer.allocateDirect(8 * 4 + 8 * 2);
        for (var row = 0; row < 4; row++) {
            for (var column = 0; column < 6; column++) {
                bytes.put(row * 8 + column, (byte) (column + 10 * row));
            }
        }
        for (var row = 0; row < 2; row++) {
            for (var pair = 0; pair < 3; pair++) {
                bytes.put(32 + row * 8 + pair * 2, (byte) (100 + pair));
                bytes.put(32 + row * 8 + pair * 2 + 1, (byte) (200 + row));
            }
        }
        return new Nv12Frame(bytes, 6, 4, 0, 8, 32, 8, VideoFrame.ColorMatrix.BT601, true, 5);
    }

    @Test
    @DisplayName("copies a frame's rows without its padding, keeping its colour and time")
    void copies() {
        var picture = new PlanePool(6, 4).copy(frame(), 77);
        assertAll(
                () -> assertEquals(PixelFormat.NV12, picture.format()),
                () -> assertEquals(6, picture.stride(0)),
                () -> assertEquals(6, picture.stride(1)),
                () -> assertEquals(35, picture.sample(0, 5, 3)),
                () -> assertEquals(102, picture.sample(1, 4, 1)),
                () -> assertEquals(201, picture.sample(1, 5, 1)),
                () -> assertEquals(VideoFrame.ColorMatrix.BT601, picture.matrix()),
                () -> assertEquals(true, picture.fullRange()),
                () -> assertEquals(77, picture.ptsNanos()));
    }

    @Test
    @DisplayName("writes three slots in turn, so a picture lasts two more")
    void turns() {
        var pool = new PlanePool(6, 4);
        var first = pool.copy(frame(), 0);
        pool.next();
        pool.next();
        var fourth = pool.next();
        assertEquals(0, fourth.index(), "the fourth picture reuses the first's memory");
        fourth.luma().put(0, (byte) 99);
        assertEquals(99, first.sample(0, 0, 0));
    }

    @Test
    @DisplayName("refuses a frame of another size, and a frame that does not fit its buffer")
    void refuses() {
        assertAll(
                () -> assertThrows(IllegalArgumentException.class, () -> new PlanePool(4, 4).copy(frame(), 0)),
                () -> assertThrows(
                        IllegalArgumentException.class,
                        () -> new Nv12Frame(
                                ByteBuffer.allocateDirect(10),
                                6,
                                4,
                                0,
                                8,
                                32,
                                8,
                                VideoFrame.ColorMatrix.BT601,
                                false,
                                0)));
    }

    @Test
    @DisplayName("reads SDL's colorspace for its matrix and range, and takes a silent camera as BT.601 limited")
    void colorimetry() {
        // SDL_COLORSPACE_BT709_LIMITED and SDL_COLORSPACE_JPEG, as SDL_pixels.h defines them.
        assertAll(
                () -> assertEquals(new Colorimetry(VideoFrame.ColorMatrix.BT709, false), Colorimetry.of(0x21100421)),
                () -> assertEquals(new Colorimetry(VideoFrame.ColorMatrix.BT601, true), Colorimetry.of(0x220004C6)),
                () -> assertEquals(Colorimetry.UNSAID, Colorimetry.of(0)));
    }
}
