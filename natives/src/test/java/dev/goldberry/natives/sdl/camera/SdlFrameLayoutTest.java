package dev.goldberry.natives.sdl.camera;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/// Where a camera frame's planes are, from what SDL reports. Pure arithmetic,
/// so no native library: the cases are the layouts SDL's drivers and its
/// converter produce.
@DisplayName("SdlFrameLayout")
class SdlFrameLayoutTest {

    @Test
    @DisplayName("NV12 is luma then interleaved chroma at luma's pitch, half the rows")
    void nv12() {
        var layout = SdlFrameLayout.of(SdlCameraPixelFormat.NV12.value(), 640, 480, 640);
        assertAll(
                () -> assertEquals(List.of(0, 640 * 480), layout.offsets()),
                () -> assertEquals(List.of(640, 640), layout.strides()),
                () -> assertEquals(640L * 480 * 3 / 2, layout.bytes()),
                () -> assertEquals(2, layout.planes()));
    }

    @Test
    @DisplayName("a driver's padded pitch carries into the chroma plane, and an odd one rounds to a pair")
    void paddedAndOdd() {
        var padded = SdlFrameLayout.of(SdlCameraPixelFormat.NV12.value(), 1280, 720, 1344);
        var odd = SdlFrameLayout.of(SdlCameraPixelFormat.NV21.value(), 5, 5, 5);
        assertAll(
                () -> assertEquals(List.of(1344, 1344), padded.strides()),
                () -> assertEquals(1344L * 720 + 1344L * 360, padded.bytes()),
                () -> assertEquals(List.of(5, 6), odd.strides()),
                () -> assertEquals(25L + 6 * 3, odd.bytes()));
    }

    @Test
    @DisplayName("three-plane formats have chroma rows half as long")
    void threePlanes() {
        var layout = SdlFrameLayout.of(SdlCameraPixelFormat.IYUV.value(), 320, 240, 320);
        assertAll(
                () -> assertEquals(List.of(0, 76_800, 76_800 + 19_200), layout.offsets()),
                () -> assertEquals(List.of(320, 160, 160), layout.strides()),
                () -> assertEquals(76_800L + 2 * 19_200, layout.bytes()));
    }

    @Test
    @DisplayName("packed formats and unnamed ones are one plane of pitch × height; MJPG is pitch bytes")
    void packedAndCompressed() {
        var yuy2 = SdlFrameLayout.of(SdlCameraPixelFormat.YUY2.value(), 640, 480, 1280);
        var rgb = SdlFrameLayout.of(0x16161804, 4, 4, 16);
        var mjpg = SdlFrameLayout.of(SdlCameraPixelFormat.MJPG.value(), 1920, 1080, 81_234);
        assertAll(
                () -> assertEquals(1280L * 480, yuy2.bytes()),
                () -> assertEquals(1, yuy2.planes()),
                () -> assertEquals(64L, rgb.bytes()),
                () -> assertEquals(81_234L, mjpg.bytes()));
    }

    @Test
    @DisplayName("refuses a frame with no size or pitch")
    void refuses() {
        assertThrows(
                IllegalArgumentException.class, () -> SdlFrameLayout.of(SdlCameraPixelFormat.NV12.value(), 0, 1, 1));
        assertThrows(
                IllegalArgumentException.class, () -> SdlFrameLayout.of(SdlCameraPixelFormat.NV12.value(), 1, 1, 0));
    }
}
