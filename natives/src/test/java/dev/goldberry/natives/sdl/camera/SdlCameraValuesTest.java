package dev.goldberry.natives.sdl.camera;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/// The camera package's values, read and refused. The constants themselves are
/// checked against SDL by the layout probe; these are the Java-side rules.
@DisplayName("SDL camera values")
class SdlCameraValuesTest {

    @Test
    @DisplayName("an enumerator a later SDL adds reads as the safe answer")
    void unknownValues() {
        assertAll(
                () -> assertEquals(SdlCameraPosition.UNKNOWN, SdlCameraPosition.of(7)),
                () -> assertEquals(SdlCameraPosition.FRONT_FACING, SdlCameraPosition.of(1)),
                () -> assertEquals(SdlCameraPermission.PENDING, SdlCameraPermission.of(9)),
                () -> assertEquals(SdlCameraPermission.DENIED, SdlCameraPermission.of(-1)),
                () -> assertEquals(Optional.empty(), SdlCameraPixelFormat.of(0x16161804)),
                () -> assertEquals(Optional.of(SdlCameraPixelFormat.YUY2), SdlCameraPixelFormat.of(0x32595559)));
    }

    @Test
    @DisplayName("only Motion JPEG is compressed")
    void compressed() {
        for (var format : SdlCameraPixelFormat.values()) {
            assertEquals(format == SdlCameraPixelFormat.MJPG, format.compressed(), format.name());
        }
    }

    @Test
    @DisplayName("a spec knows its rate, keeps an unnamed format, and changes format alone")
    void spec() {
        var spec = new SdlCameraSpec(SdlCameraPixelFormat.YUY2.value(), 3, 1280, 720, 30_000, 1001);
        var unnamed = new SdlCameraSpec(0x16161804, 0, 4, 4, 0, 0);
        var asNv12 = spec.withFormat(SdlCameraPixelFormat.NV12);
        assertAll(
                () -> assertEquals(29.97, spec.framesPerSecond(), 0.01),
                () -> assertEquals(0, unnamed.framesPerSecond()),
                () -> assertTrue(unnamed.pixelFormat().isEmpty()),
                () -> assertEquals(Optional.of(SdlCameraPixelFormat.NV12), asNv12.pixelFormat()),
                () -> assertEquals(spec.width(), asNv12.width()),
                () -> assertEquals(spec.colorspace(), asNv12.colorspace()),
                () -> assertEquals(
                        SdlCameraPixelFormat.NV12.value(),
                        SdlCameraSpec.of(SdlCameraPixelFormat.NV12, 2, 2, 30, 1).format()));
    }

    @Test
    @DisplayName("refuses a spec with no size, a camera with no id, a rate below zero")
    void refuses() {
        assertThrows(IllegalArgumentException.class, () -> new SdlCameraSpec(0, 0, 0, 1, 0, 0));
        assertThrows(IllegalArgumentException.class, () -> new SdlCameraSpec(0, 0, 1, 1, -1, 1));
        assertThrows(
                IllegalArgumentException.class, () -> new SdlCameraInfo(0, "", SdlCameraPosition.UNKNOWN, List.of()));
        assertFalse(new SdlCameraInfo(3, "cam", SdlCameraPosition.UNKNOWN, List.of())
                .name()
                .isEmpty());
    }
}
