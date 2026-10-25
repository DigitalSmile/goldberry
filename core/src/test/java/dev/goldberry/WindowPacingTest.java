package dev.goldberry;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import dev.goldberry.render.backend.headless.HeadlessBackend;
import dev.goldberry.render.backend.headless.HeadlessWindow;
import dev.goldberry.render.model.LogicalSize;
import dev.goldberry.render.window.PresentMode;
import dev.goldberry.render.window.WindowSpec;

/// A window's present mode and frame cap, through the public API: kept as
/// asked, reported as in effect, and handed to the frame statistics a `hud`
/// reads.
@DisplayName("a window's present mode and frame cap")
class WindowPacingTest {

    private HeadlessBackend backend;

    @BeforeEach
    void installBackend() {
        RendererRequirement.enforce();
        backend = new HeadlessBackend();
        GoldberryRuntime.install(backend);
    }

    @AfterEach
    void shutDown() {
        GoldberryRuntime.shutdown();
    }

    @Test
    @Timeout(10)
    @DisplayName("keep the request where they cannot apply it, and reach the frame statistics")
    void keptAndReported() {
        var window = Window.open(WindowSpec.of("pacing", LogicalSize.of(80f, 60f)));
        var backendWindow = (HeadlessWindow) backend.windows().getFirst();
        assertEquals(PresentMode.VSYNC, window.presentMode());
        assertEquals(0, window.frameCap());

        window.presentMode(PresentMode.MAILBOX).frameCap(144);

        assertEquals(PresentMode.MAILBOX, backendWindow.requestedPresentMode(), "kept");
        assertEquals(PresentMode.VSYNC, window.presentMode(), "nothing here presents in anything else");
        assertEquals(144, window.frameCap());
        assertThrows(IllegalArgumentException.class, () -> window.frameCap(-1));
        assertThrows(NullPointerException.class, () -> window.presentMode(null));

        window.onPaint(frame -> Goldberry.stop());
        Goldberry.run();

        assertEquals(PresentMode.VSYNC, window.frames().presentMode());
        assertEquals(144, window.frames().frameCap(), "the hud reads what the frame was held to");
    }
}
