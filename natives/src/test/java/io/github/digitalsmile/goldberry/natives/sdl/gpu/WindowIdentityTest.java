package io.github.digitalsmile.goldberry.natives.sdl.gpu;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.EnumSet;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import io.github.digitalsmile.goldberry.natives.sdl.Sdl;
import io.github.digitalsmile.goldberry.natives.sdl.SdlVideo;
import io.github.digitalsmile.goldberry.natives.sdl.SdlWindowHandle;
import io.github.digitalsmile.goldberry.natives.sdl.window.NativeWindowHandle;
import io.github.digitalsmile.goldberry.natives.sdl.window.SdlWindowFlag;

/// An X11 window keeps its id when a swapchain is given back and the window
/// surface is asked for again (ADR-0491).
///
/// SDL's default window surface on X11 is its OpenGL renderer, and building
/// that on a window a Vulkan swapchain was released from destroys the X window
/// and makes a new one. An embedded page is a child of the old one, so X
/// destroyed the page with it, and GTK's next request about the page ended the
/// process. With [Sdl#FRAMEBUFFER_ACCELERATION_HINT] at `0`, as `Sdl3Backend`
/// sets it on X11, the surface is the driver's own framebuffer and the window
/// is left alone.
///
/// Only under X11: set `-Pgoldberry.gpu.videoDriver=x11` where SDL would
/// choose Wayland. Skipped elsewhere, because nothing else recreates.
@Tag(GpuTestLauncher.TAG)
@DisplayName("an X11 window, across the GPU and back")
class WindowIdentityTest {

    private static SdlGpuDevice device;

    @BeforeAll
    static void createDevice() {
        device = GpuDeviceRequirement.enforce();
        Assumptions.assumeTrue("x11".equals(Sdl.get().videoDriver()), "only X11 recreates a window for its surface");
        // Before the first surface of this video initialisation, which is
        // when SDL reads it.
        Sdl.get().setHint(Sdl.FRAMEBUFFER_ACCELERATION_HINT, "0");
    }

    @AfterAll
    static void destroyDevice() {
        // Skipped before SDL was reached: nothing to give back, and no library to
        // call. Calling it anyway failed the class, and a build without it (ADR-0495).
        if (device == null) {
            return;
        }
        // Empty is unset, so the next class gets SDL's default.
        Sdl.get().setHint(Sdl.FRAMEBUFFER_ACCELERATION_HINT, "");
        device.close();
        Sdl.get().quit();
    }

    private static long xid(SdlWindowHandle window) {
        return SdlVideo.get()
                .nativeHandle(window)
                .filter(handle -> handle.kind() == NativeWindowHandle.Kind.X11)
                .map(NativeWindowHandle::value)
                .orElseThrow(() -> new AssertionError("an X11 window with no X11 id"));
    }

    /// The order a window with a page goes through: composited from its first
    /// frame, so it never had a surface, then given back when the page arrives
    /// (`Sdl3Window.stayOnTheCpu`), and its first surface made on the frame
    /// after, by which time the page is its child.
    @Test
    @DisplayName("keeps its X11 id when its first surface comes after a swapchain")
    void keepsItsId() {
        var video = SdlVideo.get();
        var window = video.createWindow("goldberry window identity", 64, 48, EnumSet.of(SdlWindowFlag.HIDDEN));
        try {
            var before = xid(window);

            device.claimWindow(window).close();
            video.acquireSurface(window);

            assertEquals(
                    before, xid(window), "SDL made a new X window, and anything parented into the old one is gone");
        } finally {
            video.destroyWindow(window);
        }
    }
}
