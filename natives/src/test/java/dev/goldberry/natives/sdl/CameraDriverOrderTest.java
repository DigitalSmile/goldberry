package dev.goldberry.natives.sdl;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeFalse;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import java.util.Set;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import dev.goldberry.natives.NativeLibraryRequirement;
import dev.goldberry.natives.NativePlatform;
import dev.goldberry.natives.sdl.camera.SdlCameras;

/// The camera drivers SDL is told to try: PipeWire before V4L2 on Linux, so a
/// camera only PipeWire serves is listed; SDL's own order elsewhere.
@DisplayName("the camera driver order")
class CameraDriverOrderTest {

    @Test
    @DisplayName("puts PipeWire before V4L2 on Linux, and keeps SDL's order on macOS and Windows")
    void order() {
        assertAll(
                () -> assertEquals(
                        Optional.of("pipewire,v4l2"), Sdl.cameraDrivers(NativePlatform.OperatingSystem.LINUX)),
                () -> assertEquals(Optional.empty(), Sdl.cameraDrivers(NativePlatform.OperatingSystem.MACOS)),
                () -> assertEquals(Optional.empty(), Sdl.cameraDrivers(NativePlatform.OperatingSystem.WINDOWS)));
    }

    @Test
    @DisplayName("starts PipeWire on Linux where its daemon runs, and V4L2 where none does")
    void startsOneOfThem() {
        NativeLibraryRequirement.enforce();
        assumeTrue(NativePlatform.current().os() == NativePlatform.OperatingSystem.LINUX, "Linux only");
        assumeTrue(System.getenv(Sdl.CAMERA_DRIVER_HINT) == null, "the environment names a driver");
        assumeFalse(Sdl.get().wasInit().contains(SdlSubsystem.CAMERA), "something else holds the camera subsystem");
        Sdl.get().setHint(Sdl.CAMERA_DRIVER_HINT, Sdl.defaultCameraDrivers().orElse(""));
        try (var cameras = SdlCameras.open()) {
            var driver = cameras.driver().orElseThrow();
            if (pipewireRuns()) {
                // The patched driver: it starts where the daemon answers.
                assertEquals("pipewire", driver);
            } else {
                assertTrue(Set.of("pipewire", "v4l2").contains(driver), driver);
            }
        }
    }

    /// Whether a PipeWire daemon is listening for this user, as
    /// `$XDG_RUNTIME_DIR/pipewire-0`.
    private static boolean pipewireRuns() {
        var runtime = System.getenv("XDG_RUNTIME_DIR");
        return runtime != null && Files.exists(Path.of(runtime, "pipewire-0"));
    }
}
