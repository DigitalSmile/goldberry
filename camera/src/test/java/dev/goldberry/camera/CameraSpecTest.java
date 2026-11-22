package dev.goldberry.camera;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/// Which mode a camera opens for what was asked.
@DisplayName("a camera spec")
class CameraSpecTest {

    /// What a Logitech C922 offers, in part: uncompressed is slow at large
    /// sizes, and Motion JPEG is fast at every size.
    private static final List<CameraFormat> C922 = List.of(
            new CameraFormat(1920, 1080, 30, 1, true),
            new CameraFormat(1280, 720, 60, 1, true),
            new CameraFormat(1280, 720, 30, 1, true),
            new CameraFormat(640, 480, 30, 1, true),
            new CameraFormat(1920, 1080, 5, 1, false),
            new CameraFormat(1280, 720, 10, 1, false),
            new CameraFormat(960, 720, 15, 1, false),
            new CameraFormat(640, 480, 30, 1, false),
            new CameraFormat(320, 240, 30, 1, false));

    @Test
    @DisplayName("opens Motion JPEG for a call the uncompressed modes are too slow for, at the rate asked")
    void call() {
        assertEquals(Optional.of(new CameraFormat(1280, 720, 30, 1, true)), CameraSpec.CALL.choose(C922));
    }

    @Test
    @DisplayName("opens an uncompressed mode where it is as near as a compressed one")
    void uncompressedWhenAsNear() {
        assertEquals(Optional.of(new CameraFormat(640, 480, 30, 1, false)), CameraSpec.PREVIEW.choose(C922));
    }

    @Test
    @DisplayName("prefers the rate to the size among uncompressed modes, as before")
    void rateBeforeSize() {
        var uncompressed = C922.stream().filter(format -> !format.compressed()).toList();
        assertEquals(Optional.of(new CameraFormat(640, 480, 30, 1, false)), CameraSpec.CALL.choose(uncompressed));
    }

    @Test
    @DisplayName("takes the exact mode where there is one")
    void exact() {
        assertEquals(Optional.of(new CameraFormat(320, 240, 30, 1, false)), new CameraSpec(320, 240, 30).choose(C922));
    }

    @Test
    @DisplayName("takes the larger size when a still picture asks for a big one at any rate")
    void still() {
        assertEquals(
                Optional.of(new CameraFormat(1920, 1080, 5, 1, false)), new CameraSpec(1920, 1080, 5).choose(C922));
    }

    @Test
    @DisplayName("opens a camera that only compresses, finds nothing in one with no mode, and refuses a bad spec")
    void refuses() {
        assertAll(
                () -> assertEquals(
                        Optional.of(new CameraFormat(1280, 720, 30, 1, true)),
                        CameraSpec.CALL.choose(List.of(new CameraFormat(1280, 720, 30, 1, true)))),
                () -> assertEquals(Optional.empty(), CameraSpec.CALL.choose(List.of())),
                () -> assertThrows(IllegalArgumentException.class, () -> new CameraSpec(0, 480, 30)),
                () -> assertThrows(IllegalArgumentException.class, () -> new CameraSpec(640, 480, 0)),
                () -> assertThrows(IllegalArgumentException.class, () -> new CameraFormat(640, 480, -1, 1, false)));
    }

    @Test
    @DisplayName("reads a mode's rate as a fraction, and a mode that gives none as zero")
    void rate() {
        assertAll(
                () -> assertEquals(29.97, new CameraFormat(640, 480, 30000, 1001, false).framesPerSecond(), 0.01),
                () -> assertEquals(0, new CameraFormat(640, 480, 0, 0, false).framesPerSecond()),
                () -> assertEquals(
                        List.of(new CameraFormat(640, 480, 30, 1, false)),
                        new CameraInfo(
                                        1,
                                        "",
                                        CameraPosition.UNKNOWN,
                                        List.of(
                                                new CameraFormat(1280, 720, 30, 1, true),
                                                new CameraFormat(640, 480, 30, 1, false)))
                                .uncompressed()));
    }
}
