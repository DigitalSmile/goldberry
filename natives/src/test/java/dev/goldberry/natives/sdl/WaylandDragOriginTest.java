package dev.goldberry.natives.sdl;

import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.EnumSet;
import java.util.Set;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import dev.goldberry.natives.NativeLibraryRequirement;
import dev.goldberry.natives.sdl.window.SdlWindowFlag;

/// What libgoldberry reads from SDL's private Wayland seat for a drag out,
/// through the real library: on Wayland, SDL's display, the window's surface
/// and SDL's data device; on any other driver, nothing.
///
/// A hidden window, so nothing appears and no seat's pointer is in it: the
/// first seat with a data device answers, which is what the C side falls back
/// to. Skipped where the driver asked for will not start.
@DisplayName("where a drag out starts on Wayland, read from SDL's seat")
class WaylandDragOriginTest {

    @BeforeAll
    static void requireNativeLibrary() {
        NativeLibraryRequirement.enforce();
    }

    @AfterEach
    void shutDownSdl() {
        Sdl.get().quit();
        Sdl.get().setHint(Sdl.VIDEO_DRIVER_HINT, "");
        Sdl.get().clearError();
    }

    @Test
    @DisplayName("answers SDL's display, the window's surface and SDL's data device on Wayland")
    void onWayland() {
        Assumptions.assumeTrue(System.getenv("WAYLAND_DISPLAY") != null, "no Wayland compositor here");
        start("wayland");
        var video = SdlVideo.get();
        var window = video.createWindow("drag origin", 120, 80, EnumSet.of(SdlWindowFlag.HIDDEN));
        try {
            var origin = video.waylandDragOrigin(window);

            assertTrue(origin.isPresent(), "no data device on a compositor that has one");
            assertNotEquals(0, origin.get().display());
            assertNotEquals(0, origin.get().surface());
            assertNotEquals(0, origin.get().dataDevice());
        } finally {
            video.destroyWindow(window);
        }
    }

    @Test
    @DisplayName("answers nothing on X11")
    void onX11() {
        Assumptions.assumeTrue(System.getenv("DISPLAY") != null, "no X server here");
        start("x11");
        var video = SdlVideo.get();
        var window = video.createWindow("drag origin", 120, 80, EnumSet.of(SdlWindowFlag.HIDDEN));
        try {
            assertTrue(video.waylandDragOrigin(window).isEmpty());
        } finally {
            video.destroyWindow(window);
        }
    }

    private static void start(String driver) {
        Sdl.get().setHint(Sdl.VIDEO_DRIVER_HINT, driver);
        try {
            Sdl.get().initialize(Set.of(SdlSubsystem.VIDEO));
        } catch (SdlException e) {
            Assumptions.abort("SDL's " + driver + " driver will not start here: " + e.getMessage());
        }
        Assumptions.assumeTrue(
                driver.equals(Sdl.get().videoDriver()),
                () -> "SDL chose " + Sdl.get().videoDriver());
    }
}
