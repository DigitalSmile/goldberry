package dev.goldberry.render.window;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/// The words a window's presentation is logged and shown in (ADR-0492).
@DisplayName("a window's presentation")
class PresentationTest {

    @Test
    @DisplayName("on the GPU, names the driver in every form")
    void gpu() {
        var gpu = new Presentation.Gpu("vulkan");

        assertEquals("GPU", gpu.path());
        assertEquals("through the GPU (vulkan)", gpu.describe());
        assertEquals("GPU · vulkan", gpu.label());
    }

    @Test
    @DisplayName("on the CPU, says why in every form")
    void cpu() {
        var cpu = new Presentation.Cpu("goldberry.gpu=off");

        assertEquals("CPU", cpu.path());
        assertEquals("on the CPU: goldberry.gpu=off", cpu.describe());
        assertEquals("CPU · goldberry.gpu=off", cpu.label());
    }

    @Test
    @DisplayName("is undecided on the CPU before a first frame")
    void undecided() {
        assertInstanceOf(Presentation.Cpu.class, Presentation.Cpu.UNDECIDED);
    }

    @Test
    @DisplayName("refuses a missing driver or reason")
    void nulls() {
        assertThrows(NullPointerException.class, () -> new Presentation.Gpu(null));
        assertThrows(NullPointerException.class, () -> new Presentation.Cpu(null));
    }
}
