package io.github.digitalsmile.goldberry.gpu.render;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import io.github.digitalsmile.goldberry.natives.NativeLibraryRequirement;
import io.github.digitalsmile.goldberry.natives.sdl.gpu.GpuDeviceRequirement;
import io.github.digitalsmile.goldberry.natives.sdl.gpu.GpuTestLauncher;
import io.github.digitalsmile.goldberry.render.DamageRect;
import io.github.digitalsmile.goldberry.render.PixelBuffer;
import io.github.digitalsmile.goldberry.render.backend.sdl3.Sdl3Backend;
import io.github.digitalsmile.goldberry.render.model.LogicalSize;
import io.github.digitalsmile.goldberry.render.model.PixelFormat;
import io.github.digitalsmile.goldberry.render.window.WindowSpec;

/// The whole path, through the sdl3 backend: by default a window finds `:gpu`'s
/// compositor by `ServiceLoader`, lends no surface, and presents its frames
/// through the GPU; with `goldberry.gpu=off` it presents through its surface
/// (ADR-0479, ADR-0480).
///
/// A window that did not become composited would lend its surface, so
/// `acquireFrame` being empty is what says it did.
@Tag(GpuTestLauncher.TAG)
@DisplayName("a composited window, through the sdl3 backend")
class CompositedBackendTest {

    @BeforeEach
    void composite() {
        NativeLibraryRequirement.enforce();
        var videoDriver = System.getProperty(GpuDeviceRequirement.VIDEO_DRIVER_PROPERTY, "");
        if (!videoDriver.isBlank()) {
            System.setProperty(Sdl3Backend.VIDEO_DRIVER_PROPERTY, videoDriver);
        }
    }

    @AfterEach
    void restore() {
        System.clearProperty("goldberry.gpu");
        System.clearProperty(Sdl3Backend.VIDEO_DRIVER_PROPERTY);
    }

    @Test
    @DisplayName("by default lends no surface and presents frames, whole and then damaged, through the GPU")
    void presentsThroughTheGpu() {
        try (var backend = new Sdl3Backend()) {
            var window = backend.createWindow(WindowSpec.of("composited", LogicalSize.of(120, 80)));
            assertTrue(window.acquireFrame().isEmpty(), "a composited window lends no surface");
            var size = window.physicalSize();
            var frame = PixelBuffer.allocate(size, PixelFormat.BGRA32_PREMULTIPLIED);
            for (var i = 0; i < size.width() * size.height(); i++) {
                frame.pixels().putInt(i * 4, 0xFF336699);
            }
            window.present(frame, List.of(DamageRect.all(size)));
            for (var step = 0; step < 3; step++) {
                assertTrue(window.acquireFrame().isEmpty(), "and stays composited");
                window.present(frame, List.of(new DamageRect(step, step, 10, 10)));
            }
            window.close();
        }
    }

    @Test
    @DisplayName("with the GPU off, lends its surface and presents through it as before")
    void gpuOff() {
        System.setProperty("goldberry.gpu", "off");
        try (var backend = new Sdl3Backend()) {
            var window = backend.createWindow(WindowSpec.of("on the CPU", LogicalSize.of(120, 80)));
            var surface = window.acquireFrame();
            assertTrue(surface.isPresent(), "a window on the CPU lends its surface");
            window.present(surface.get(), List.of(DamageRect.all(window.physicalSize())));
            assertTrue(!window.lastPresent().composited(), "and reports no GPU present");
            window.close();
        }
    }
}
