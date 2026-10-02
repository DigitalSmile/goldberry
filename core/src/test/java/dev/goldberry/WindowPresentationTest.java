package dev.goldberry;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import dev.goldberry.render.backend.headless.HeadlessBackend;
import dev.goldberry.render.backend.headless.HeadlessWindow;
import dev.goldberry.render.model.LogicalSize;
import dev.goldberry.render.window.Presentation;
import dev.goldberry.render.window.WindowSpec;

/// A window says how its frames reach the screen, and says it again only when
/// that changes.
@DisplayName("a window's presentation, through the frame loop")
class WindowPresentationTest {

    private static final Presentation VULKAN = new Presentation.Gpu("vulkan");

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
    @DisplayName("is decided at the first frame, heard once per change, and counted every frame")
    void heardOncePerChange() {
        var window = Window.open(WindowSpec.of("presentation", LogicalSize.of(80f, 60f)));
        var backendWindow = (HeadlessWindow) backend.windows().getFirst();
        var heard = new ArrayList<Presentation>();
        var before = window.presentation();
        window.onPresentationChange(heard::add);
        var painted = new int[1];
        window.onPaint(frame -> {
            painted[0]++;
            // From the second frame on, the window says it is on the GPU.
            if (painted[0] == 2) {
                backendWindow.presentAs(VULKAN);
            }
            if (painted[0] >= 3) {
                Goldberry.stop();
            } else {
                window.repaint();
            }
        });

        Goldberry.run();

        assertEquals(Presentation.Cpu.UNDECIDED, before, "decided before any frame was presented");
        assertEquals(
                List.of(new Presentation.Cpu("headless: nothing is shown on a screen"), VULKAN),
                heard,
                "the first frame, then the change; the third frame changed nothing");
        assertEquals(VULKAN, window.presentation());
        assertEquals(2, window.presentations().gpuFrames());
        assertEquals(1, window.presentations().cpuFrames());
    }
}
