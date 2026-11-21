package dev.goldberry.natives.sdl.camera;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeFalse;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import dev.goldberry.natives.NativeLibraryRequirement;
import dev.goldberry.natives.sdl.Sdl;
import dev.goldberry.natives.sdl.SdlException;
import dev.goldberry.natives.sdl.SdlSubsystem;

/// The camera list against SDL's `dummy` camera driver, which lists none: what
/// CI and a machine without a camera see. A real camera is a hand check.
@DisplayName("SdlCameras, on SDL's dummy camera driver")
class SdlCamerasTest {

    private static final String CAMERA_DRIVER = "SDL_CAMERA_DRIVER";

    @BeforeEach
    void dummyDriver() {
        NativeLibraryRequirement.enforce();
        assumeFalse(Sdl.get().wasInit().contains(SdlSubsystem.CAMERA), "something else holds the camera subsystem");
        Sdl.get().setHint(CAMERA_DRIVER, "dummy");
    }

    @AfterEach
    void defaultDriver() {
        Sdl.get().setHint(CAMERA_DRIVER, "");
    }

    @Test
    @DisplayName("starts the named driver, lists no camera, and releases the subsystem on close")
    void listsNothing() {
        try (var cameras = SdlCameras.open()) {
            assertEquals(Optional.of("dummy"), cameras.driver());
            assertEquals(List.of(), cameras.cameras());
            assertTrue(Sdl.get().wasInit().contains(SdlSubsystem.CAMERA));
        }
        assertTrue(!Sdl.get().wasInit().contains(SdlSubsystem.CAMERA), "the reference was released");
    }

    @Test
    @DisplayName("opening a camera that is not there is SDL's refusal, not a crash")
    void opensNothing() {
        try (var cameras = SdlCameras.open()) {
            assertThrows(SdlException.class, () -> cameras.open(1, null));
            assertThrows(
                    SdlException.class,
                    () -> cameras.open(1, SdlCameraSpec.of(SdlCameraPixelFormat.NV12, 640, 480, 30, 1)));
        }
    }

    @Test
    @DisplayName("refuses to be used once closed")
    void closed() {
        var cameras = SdlCameras.open();
        cameras.close();
        cameras.close();
        assertThrows(IllegalStateException.class, cameras::cameras);
    }
}
