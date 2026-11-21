package dev.goldberry.natives.sdl.camera;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeFalse;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.util.Comparator;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.LockSupport;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import dev.goldberry.natives.NativeLibraryRequirement;
import dev.goldberry.natives.sdl.Sdl;
import dev.goldberry.natives.sdl.SdlSubsystem;

/// A real camera, where there is one: opened in NV12 from its smallest
/// uncompressed mode, so SDL's camera thread converts, and a few frames read
/// in place. Skips on a machine without a camera, which CI is, and on macOS,
/// where opening one waits for a person to allow it.
@DisplayName("SdlCamera, on a real camera")
class SdlCameraLiveTest {

    private record Mode(int id, SdlCameraSpec spec) {}

    @BeforeEach
    void requireCamera() {
        NativeLibraryRequirement.enforce();
        assumeFalse(System.getProperty("os.name", "").toLowerCase().startsWith("mac"), "macOS asks the user first");
        assumeFalse(Sdl.get().wasInit().contains(SdlSubsystem.CAMERA), "something else holds the camera subsystem");
    }

    @Test
    @Timeout(value = 20, unit = TimeUnit.SECONDS)
    @DisplayName("lends NV12 frames laid out as SdlFrameLayout says, and takes each back")
    void framesInNv12() {
        try (var cameras = SdlCameras.open()) {
            var mode = cameras.cameras().stream()
                    .flatMap(info -> info.formats().stream()
                            .filter(spec -> spec.pixelFormat()
                                    .filter(f -> !f.compressed())
                                    .isPresent())
                            .map(spec -> new Mode(info.id(), spec)))
                    .min(Comparator.comparingInt(candidate -> candidate.spec().width()));
            assumeTrue(mode.isPresent(), "no camera with an uncompressed mode");
            var id = mode.get().id();
            var spec = mode.get().spec().withFormat(SdlCameraPixelFormat.NV12);
            try (var camera = cameras.open(id, spec)) {
                var permission = camera.permission();
                while (permission == SdlCameraPermission.PENDING) {
                    LockSupport.parkNanos(20_000_000L);
                    permission = camera.permission();
                }
                assumeTrue(permission == SdlCameraPermission.APPROVED, "the camera was refused");
                var last = -1L;
                for (var seen = 0; seen < 3; ) {
                    Optional<SdlCameraFrame> acquired = camera.acquire();
                    if (acquired.isEmpty()) {
                        LockSupport.parkNanos(5_000_000L);
                        continue;
                    }
                    var frame = acquired.get();
                    try {
                        var layout = SdlFrameLayout.of(
                                frame.format(),
                                frame.width(),
                                frame.height(),
                                frame.layout().strides().getFirst());
                        var stamp = frame.timestampNanos();
                        var previous = last;
                        assertAll(
                                () -> assertEquals(Optional.of(SdlCameraPixelFormat.NV12), frame.pixelFormat()),
                                () -> assertEquals(spec.width(), frame.width()),
                                () -> assertEquals(spec.height(), frame.height()),
                                () -> assertEquals(2, frame.layout().planes()),
                                () -> assertEquals(layout, frame.layout()),
                                () -> assertEquals(
                                        frame.layout().bytes(), frame.pixels().capacity()),
                                () -> assertEquals(1, camera.framesLent()),
                                () -> assertTrue(stamp == 0 || stamp > previous, "timestamps move on"));
                        last = stamp;
                    } finally {
                        frame.close();
                    }
                    assertThrows(IllegalStateException.class, frame::pixels, "a released frame is not readable");
                    seen++;
                }
                assertEquals(0, camera.framesLent());
                assertTrue(camera.format().isPresent());
            }
        }
    }
}
