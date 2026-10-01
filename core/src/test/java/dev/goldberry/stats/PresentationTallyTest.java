package dev.goldberry.stats;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import dev.goldberry.render.window.Presentation;

/// The exit line that says where a window's frames went (ADR-0492).
@DisplayName("a window's frames, by the path they took")
class PresentationTallyTest {

    @Test
    @DisplayName("counts nothing before a frame")
    void empty() {
        assertEquals("0 frame(s) through the GPU, 0 on the CPU", new PresentationTally().describe());
    }

    @Test
    @DisplayName("counts each path, and names every driver the GPU frames went through")
    void counts() {
        var tally = new PresentationTally();
        tally.count(new Presentation.Gpu("vulkan"));
        tally.count(new Presentation.Gpu("vulkan"));
        tally.count(new Presentation.Cpu("a page is embedded in it"));

        assertEquals(2, tally.gpuFrames());
        assertEquals(1, tally.cpuFrames());
        assertEquals("2 frame(s) through the GPU (vulkan), 1 on the CPU", tally.describe());
    }
}
