package dev.goldberry.media.platform.windows;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Optional;
import java.util.OptionalInt;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import dev.goldberry.media.codec.VideoFrame;

/// Where the planes are, what is shown, and in what colour, from what an MFT's
/// output type reports.
@DisplayName("PictureLayout")
class PictureLayoutTest {

    /// An `MFVideoArea` as Windows writes it.
    private static byte[] videoArea(int x, int y, int width, int height) {
        return ByteBuffer.allocate(16)
                .order(ByteOrder.LITTLE_ENDIAN)
                .putShort((short) 0) // OffsetX.fract
                .putShort((short) x) // OffsetX.value
                .putShort((short) 0) // OffsetY.fract
                .putShort((short) y) // OffsetY.value
                .putInt(width)
                .putInt(height)
                .array();
    }

    @Test
    @DisplayName("reads the aperture's whole offsets and size, and nothing from a blob of another size")
    void aperture() {
        assertEquals(
                Optional.of(new PictureLayout.Area(0, 0, 160, 90)), PictureLayout.aperture(videoArea(0, 0, 160, 90)));
        assertEquals(
                Optional.of(new PictureLayout.Area(8, 4, 1920, 1080)),
                PictureLayout.aperture(videoArea(8, 4, 1920, 1080)));
        assertTrue(PictureLayout.aperture(new byte[12]).isEmpty());
    }

    @Test
    @DisplayName("packs and unpacks MF_MT_FRAME_SIZE: width high, height low")
    void frameSize() {
        var packed = PictureLayout.frameSize(1920, 1088);
        assertEquals(0x0000_0780_0000_0440L, packed);
        assertEquals(1920, PictureLayout.frameWidth(packed));
        assertEquals(1088, PictureLayout.frameHeight(packed));
    }

    @Test
    @DisplayName("shows the aperture, else the frame, inside the frame and no larger than the track")
    void visible() {
        // The H.264 decoder's 160×96 frame with its 160×90 aperture.
        var aperture = Optional.of(new PictureLayout.Area(0, 0, 160, 90));
        assertEquals(new PictureLayout.Area(0, 0, 160, 90), PictureLayout.visible(aperture, 160, 96, 160, 90));
        // No aperture: the frame, clamped to the track's size.
        assertEquals(new PictureLayout.Area(0, 0, 160, 90), PictureLayout.visible(Optional.empty(), 160, 96, 160, 90));
        // A track with no size of its own: the frame.
        assertEquals(new PictureLayout.Area(0, 0, 160, 96), PictureLayout.visible(Optional.empty(), 160, 96, 0, 0));
        // An aperture past the frame's edge is cut to it; an odd origin rounds down.
        assertEquals(
                new PictureLayout.Area(2, 4, 158, 92),
                PictureLayout.visible(Optional.of(new PictureLayout.Area(3, 5, 400, 400)), 160, 96, 0, 0));
        // An empty aperture is no aperture.
        assertEquals(
                new PictureLayout.Area(0, 0, 160, 96),
                PictureLayout.visible(Optional.of(new PictureLayout.Area(0, 0, 0, 0)), 160, 96, 0, 0));
    }

    @Test
    @DisplayName("the stride is the 2D pitch, else the default stride, else the width packed")
    void stride() {
        assertEquals(256, PictureLayout.stride(OptionalInt.of(256), OptionalInt.of(192), 160, 1));
        assertEquals(192, PictureLayout.stride(OptionalInt.empty(), OptionalInt.of(192), 160, 1));
        assertEquals(160, PictureLayout.stride(OptionalInt.empty(), OptionalInt.empty(), 160, 1));
        assertEquals(320, PictureLayout.stride(OptionalInt.of(0), OptionalInt.empty(), 160, 2), "P010");
        assertThrows(
                IllegalStateException.class,
                () -> PictureLayout.stride(OptionalInt.of(-160), OptionalInt.empty(), 160, 1));
    }

    @ParameterizedTest(name = "{0} rows at {1}, {2} bytes: {3} rows")
    @CsvSource({
        // Exactly the frame.
        "96, 160, 23040, 96",
        // Unknown, as for a 2D buffer.
        "96, 160, -1, 96",
        // Padded beyond the height reported, exactly: the buffer's rows.
        "90, 160, 23040, 96",
        // Longer, but not a whole picture: the height reported.
        "90, 160, 23041, 90",
        // Odd heights have a last chroma row of their own.
        "1, 2, 4, 1",
    })
    @DisplayName("the buffer's rows are the frame's height, or what its length says exactly")
    void bufferRows(int frameHeight, int stride, long length, int rows) {
        assertEquals(rows, PictureLayout.bufferRows(frameHeight, stride, length));
    }

    @Test
    @DisplayName("the chroma plane starts after the buffer's rows, and a crop moves both planes' starts")
    void planes() {
        var nv12 = PictureLayout.planes(new PictureLayout.Area(0, 0, 160, 90), 160, 96, 1);
        assertEquals(new PictureLayout.Planes(0, 160 * 96, 160 * 96, 160 * 48), nv12);
        assertEquals(160 * 96 * 3 / 2, nv12.end());

        var p010 = PictureLayout.planes(new PictureLayout.Area(0, 0, 1920, 1080), 3840, 1088, 2);
        assertEquals(3840L * 1088, p010.chromaOffset());
        assertEquals(3840L * 544, p010.chromaSize());

        var cropped = PictureLayout.planes(new PictureLayout.Area(8, 4, 100, 80), 256, 96, 1);
        assertEquals(4 * 256 + 8, cropped.lumaOffset());
        assertEquals(256L * 96 - (4 * 256 + 8), cropped.lumaSize());
        assertEquals(256L * 96 + 2 * 256 + 8, cropped.chromaOffset());
        assertEquals(PictureLayout.twoPlaneBytes(256, 96), cropped.end());
    }

    @ParameterizedTest(name = "matrix {0} at {1} rows: {2}")
    @CsvSource({
        "1, 480, BT709",
        "2, 1080, BT601",
        "3, 480, BT709", // SMPTE 240M, as swscale takes it
        "4, 2160, BT2020",
        "5, 2160, BT2020",
        // Unknown or absent: BT.709 from 720 rows, BT.601 below.
        "0, 719, BT601",
        "0, 720, BT709",
        "-1, 90, BT601",
        "-1, 1080, BT709",
    })
    @DisplayName("the matrix is the output type's, or the default by height")
    void matrix(int transferMatrix, int height, VideoFrame.ColorMatrix expected) {
        var tagged = transferMatrix < 0 ? OptionalInt.empty() : OptionalInt.of(transferMatrix);
        assertEquals(expected, PictureLayout.matrix(tagged, height));
    }

    @Test
    @DisplayName("full range only when the output type says 0-255")
    void range() {
        assertTrue(PictureLayout.fullRange(OptionalInt.of(MfGuids.RANGE_0_255)));
        assertFalse(PictureLayout.fullRange(OptionalInt.of(MfGuids.RANGE_16_235)));
        assertFalse(PictureLayout.fullRange(OptionalInt.of(MfGuids.RANGE_UNKNOWN)));
        assertFalse(PictureLayout.fullRange(OptionalInt.empty()));
    }
}
