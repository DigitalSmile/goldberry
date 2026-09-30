package io.github.digitalsmile.goldberry.render.backend.sdl3;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import io.github.digitalsmile.goldberry.render.window.NativeHandle;

/// Which windows keep the GPU with a page in them (ADR-0491).
@DisplayName("a page's stacking against a swapchain")
class PageStackingTest {

    /// Measured: an X11 child window is drawn over its parent's Vulkan
    /// presents, which the server clips around it. Leaving the GPU for it is
    /// what took the page down with the window SDL recreated.
    @Test
    @DisplayName("under X11 the page is above the swapchain, so the window keeps the GPU")
    void x11KeepsTheGpu() {
        assertInstanceOf(PageStacking.AboveTheSwapchain.class, PageStacking.of(NativeHandle.Kind.X11));
    }

    @ParameterizedTest
    @EnumSource(
            value = NativeHandle.Kind.class,
            names = {"COCOA", "WIN32"})
    @DisplayName("where nobody has looked, the window goes to the CPU and the log says why")
    void unmeasuredGoesToTheCpu(NativeHandle.Kind kind) {
        var stacking = assertInstanceOf(PageStacking.NeedsTheCpu.class, PageStacking.of(kind));

        assertFalse(stacking.reason().isBlank(), "a window that left the GPU must say why");
    }
}
