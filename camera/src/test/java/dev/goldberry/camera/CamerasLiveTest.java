package dev.goldberry.camera;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assumptions.assumeFalse;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.util.Locale;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.LockSupport;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import dev.goldberry.media.codec.PixelFormat;
import dev.goldberry.media.picture.VideoPlanes;
import dev.goldberry.natives.NativeLibraryRequirement;

/// The system's cameras through SDL: listed on every machine, and on one with
/// a camera, opened and three pictures taken. Skips the opening on a machine
/// without one, which CI is, and on macOS, where opening waits for a person.
@DisplayName("cameras, through SDL")
class CamerasLiveTest {

    @BeforeEach
    void library() {
        NativeLibraryRequirement.enforce();
    }

    @Test
    @Timeout(value = 20, unit = TimeUnit.SECONDS)
    @DisplayName("lists what the system has, and takes NV12 pictures from the preferred camera")
    void pictures() {
        try (var cameras = Cameras.open()) {
            var listed = cameras.list();
            assumeFalse(
                    System.getProperty("os.name", "").toLowerCase(Locale.ROOT).startsWith("mac"),
                    "macOS asks the user first");
            assumeTrue(
                    listed.stream().anyMatch(camera -> !camera.uncompressed().isEmpty()),
                    "no camera with an uncompressed mode");
            var info = listed.stream()
                    .filter(camera -> !camera.uncompressed().isEmpty())
                    .findFirst()
                    .orElseThrow();
            try (var camera = cameras.open(info, CameraSpec.PREVIEW)) {
                while (camera.status() instanceof CameraStatus.Asking) {
                    LockSupport.parkNanos(20_000_000L);
                }
                assumeTrue(camera.status() instanceof CameraStatus.Active, () -> "the camera is " + camera.status());
                var seen = 0;
                Object last = null;
                while (seen < 3) {
                    var shown = camera.shownPicture();
                    if (shown.isPresent() && shown.get() != last) {
                        last = shown.get();
                        seen++;
                    }
                    LockSupport.parkNanos(5_000_000L);
                }
                var picture = assertInstanceOf(VideoPlanes.class, last);
                assertAll(
                        () -> assertEquals(PixelFormat.NV12, picture.format()),
                        () -> assertEquals(camera.format().width(), picture.width()),
                        () -> assertEquals(camera.format().height(), picture.height()));
            }
        }
    }
}
