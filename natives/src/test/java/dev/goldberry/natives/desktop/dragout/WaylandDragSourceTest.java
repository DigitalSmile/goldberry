package dev.goldberry.natives.desktop.dragout;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.foreign.MemorySegment;
import java.nio.ByteBuffer;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import dev.goldberry.natives.NativeLibraryRequirement;
import dev.goldberry.natives.sdl.Sdl;
import dev.goldberry.natives.sdl.SdlException;
import dev.goldberry.natives.sdl.SdlSubsystem;
import dev.goldberry.natives.sdl.SdlVideo;
import dev.goldberry.natives.sdl.window.SdlWindowFlag;
import dev.goldberry.natives.wayland.WaylandClient;
import dev.goldberry.natives.wayland.WaylandQueue;

/// A drag out started from a real SDL window on a real compositor, every
/// request of it on SDL's own connection: the source, the icon and
/// `start_drag` on SDL's data device.
///
/// The window is hidden and no button is down, so the serial is not a press's
/// and the compositor ignores the drag, as mutter and weston both do,
/// silently. What is pinned is that none of it is a protocol error, which
/// would end SDL's connection with the toolkit's, and that closing the drag
/// leaves the connection working.
@DisplayName("a Wayland drag out, started on SDL's own connection")
@Timeout(30)
class WaylandDragSourceTest {

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
    @DisplayName("starts, with a picture, and closes without a protocol error")
    void startsAndCloses() {
        Assumptions.assumeTrue(System.getenv("WAYLAND_DISPLAY") != null, "no Wayland compositor here");
        Sdl.get().setHint(Sdl.VIDEO_DRIVER_HINT, "wayland");
        try {
            Sdl.get().initialize(Set.of(SdlSubsystem.VIDEO));
        } catch (SdlException e) {
            Assumptions.abort("SDL's Wayland driver will not start here: " + e.getMessage());
        }
        Assumptions.assumeTrue("wayland".equals(Sdl.get().videoDriver()), "SDL chose another driver");
        var client = WaylandClient.get().orElseThrow();
        var video = SdlVideo.get();
        var window = video.createWindow("drag source", 120, 80, EnumSet.of(SdlWindowFlag.HIDDEN));
        try {
            var origin = video.waylandDragOrigin(window).orElseThrow();
            var display = MemorySegment.ofAddress(origin.display());
            var heard = new Heard();
            var icon = new DragIcon(8, 8, 32, ByteBuffer.allocateDirect(8 * 8 * 4), 2, 2, 1);
            var drag = WaylandDragSource.start(origin, data(), EnumSet.of(DragAction.COPY), icon, heard);
            Assumptions.assumeTrue(drag.isPresent(), "the compositor's data device manager is older than 3");

            try (var probe = WaylandQueue.open(client, display).orElseThrow()) {
                probe.roundtrip();
                drag.get().tick();
                assertEquals(0, client.error(display), "a protocol error on SDL's connection");

                drag.get().close();
                probe.roundtrip();
                assertEquals(0, client.error(display), "a protocol error after closing");
                assertTrue(drag.get().isClosed());
            }
        } finally {
            video.destroyWindow(window);
        }
    }

    private static DragData data() {
        return new DragData() {
            @Override
            public List<String> types() {
                return List.of("text/plain;charset=utf-8", "image/png");
            }

            @Override
            public byte @Nullable [] bytes(String mime) {
                return new byte[] {1, 2, 3};
            }
        };
    }

    private static final class Heard implements WaylandDragListener {

        @Override
        public void target(@Nullable String mime) {}

        @Override
        public void dropPerformed() {}

        @Override
        public void finished(@Nullable DragAction action) {}

        @Override
        public void cancelled() {}
    }
}
