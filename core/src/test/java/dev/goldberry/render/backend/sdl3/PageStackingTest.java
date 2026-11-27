package dev.goldberry.render.backend.sdl3;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import dev.goldberry.render.window.NativeHandle;

/// Which windows keep GPU presentation while holding an embedded page.
@DisplayName("a page's stacking against a swapchain")
class PageStackingTest {

    @ParameterizedTest
    @EnumSource(
            value = NativeHandle.Kind.class,
            names = {"X11", "COCOA"})
    @DisplayName("keeps GPU presentation where the page is above the swapchain")
    void keepsTheGpu(NativeHandle.Kind kind) {
        assertInstanceOf(PageStacking.AboveTheSwapchain.class, PageStacking.of(kind));
    }

    @Test
    @DisplayName("on Win32 the window switches to CPU presentation with a reason")
    void win32GoesToTheCpu() {
        var stacking = assertInstanceOf(PageStacking.NeedsTheCpu.class, PageStacking.of(NativeHandle.Kind.WIN32));

        assertFalse(stacking.reason().isBlank(), "a window that left the GPU must say why");
    }
}
