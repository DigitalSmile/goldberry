package dev.goldberry.gpu.render;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import dev.goldberry.gpu.render.mapped.InPlaceProbe;
import dev.goldberry.natives.NativeLibraryRequirement;
import dev.goldberry.natives.sdl.gpu.GpuDeviceRequirement;
import dev.goldberry.natives.sdl.gpu.GpuTestLauncher;
import dev.goldberry.natives.sdl.gpu.SdlGpuDevice;
import dev.goldberry.render.DamageRect;
import dev.goldberry.render.PixelBuffer;
import dev.goldberry.render.backend.sdl3.Sdl3Backend;
import dev.goldberry.render.model.LogicalSize;
import dev.goldberry.render.model.PixelFormat;
import dev.goldberry.render.window.PresentMode;
import dev.goldberry.render.window.Presentation;
import dev.goldberry.render.window.WindowSpec;

/// The whole path, through the sdl3 backend: by default a window finds `:gpu`'s
/// compositor by `ServiceLoader`, lends no surface, and presents its frames
/// through the GPU; with `goldberry.gpu=off` it presents through its surface.
/// Windows present through the GPU by default and on the CPU where they cannot.
///
/// A composited window says so in its presentation. Where the device's check
/// says to paint in place, its `acquireFrame` lends the compositor's own frame,
/// and is empty otherwise; either way it is not the window surface. Read more:
/// [What the module does to a window](https://goldberry.dev/docs/components/gpu.html#what-the-module-does-to-a-window).
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
            window.acquireFrame();
            assertInstanceOf(Presentation.Gpu.class, window.presentation(), "composited from the first acquire");
            var size = window.physicalSize();
            var frame = PixelBuffer.allocate(size, PixelFormat.BGRA32_PREMULTIPLIED);
            for (var i = 0; i < size.width() * size.height(); i++) {
                frame.pixels().putInt(i * 4, 0xFF336699);
            }
            window.present(frame, List.of(DamageRect.all(size)));
            for (var step = 0; step < 3; step++) {
                window.acquireFrame();
                assertInstanceOf(Presentation.Gpu.class, window.presentation(), "and stays composited");
                window.present(frame, List.of(new DamageRect(step, step, 10, 10)));
            }
            // And says so, naming the driver it presents through.
            var presentation = assertInstanceOf(Presentation.Gpu.class, window.presentation());
            assertTrue(SdlGpuDevice.compiledDrivers().contains(presentation.driver()), presentation::toString);
            window.close();
        }
    }

    @Test
    @DisplayName("painting in place, lends one frame per size, keeps what was painted in it, and presents it")
    void paintsInPlace() {
        System.setProperty(InPlaceProbe.PROPERTY, "on");
        try (var backend = new Sdl3Backend()) {
            var window = backend.createWindow(WindowSpec.of("in place", LogicalSize.of(120, 80)));
            var size = window.physicalSize();
            var frame = window.acquireFrame().orElseThrow(() -> new AssertionError("no frame lent in place"));
            assertInstanceOf(Presentation.Gpu.class, window.presentation(), "the compositor's frame, not a surface");
            assertEquals(size, frame.size());
            frame.pixels().putInt(0, 0xFF336699);
            window.present(frame, List.of(DamageRect.all(size)));
            assertEquals(size.width() * size.height() * 4L, window.lastPresent().uploadBytes(), "the first is whole");
            var again = window.acquireFrame().orElseThrow();
            assertSame(frame, again, "the same buffer, so the frame loop repaints in part");
            assertEquals(0xFF336699, again.pixels().getInt(0), "holding what was painted last");
            again.pixels().putInt(0, 0xFF112233);
            window.present(again, List.of(new DamageRect(0, 0, 1, 1)));
            assertTrue(window.lastPresent().composited());
            assertTrue(window.lastPresent().uploadBytes() >= 4, "its damage went up");
            window.close();
        } finally {
            System.clearProperty(InPlaceProbe.PROPERTY);
        }
    }

    @Test
    @DisplayName("with painting in place off, lends nothing and stages the frame loop's own frames")
    void stagesWhenOff() {
        System.setProperty(InPlaceProbe.PROPERTY, "off");
        try (var backend = new Sdl3Backend()) {
            var window = backend.createWindow(WindowSpec.of("staged", LogicalSize.of(120, 80)));
            assertTrue(window.acquireFrame().isEmpty(), "the frame loop paints a frame of its own");
            assertInstanceOf(Presentation.Gpu.class, window.presentation());
            var size = window.physicalSize();
            window.present(PixelBuffer.allocate(size, PixelFormat.BGRA32_PREMULTIPLIED), List.of(DamageRect.all(size)));
            assertEquals(size.width() * size.height() * 4L, window.lastPresent().uploadBytes());
            window.close();
        } finally {
            System.clearProperty(InPlaceProbe.PROPERTY);
        }
    }

    @Test
    @DisplayName("presents in the mode it is asked for, or the nearest fallback its driver has, and keeps the cap")
    void presentModes() {
        try (var backend = new Sdl3Backend()) {
            var window = backend.createWindow(WindowSpec.of("present modes", LogicalSize.of(120, 80)));
            var size = window.physicalSize();
            var frame = PixelBuffer.allocate(size, PixelFormat.BGRA32_PREMULTIPLIED);
            window.acquireFrame();
            window.present(frame, List.of(DamageRect.all(size)));
            assertInstanceOf(
                    Presentation.Gpu.class, window.presentation(), "composited, so the mode is the swapchain's");
            assertEquals(PresentMode.VSYNC, window.presentMode(), "claimed in VSYNC");
            for (var mode : PresentMode.values()) {
                var inEffect = window.requestPresentMode(mode);
                assertTrue(mode.fallbacks().contains(inEffect), mode + " came out as " + inEffect);
                assertEquals(inEffect, window.presentMode());
                window.acquireFrame();
                window.present(frame, List.of(DamageRect.all(size)));
            }
            window.frameCap(30);
            assertEquals(30, window.frameCap());
            window.requestPresentMode(PresentMode.VSYNC);
            window.close();
        }
    }

    @Test
    @DisplayName("on the CPU, keeps the mode asked for and presents as its surface does")
    void presentModeOnTheCpu() {
        System.setProperty("goldberry.gpu", "off");
        try (var backend = new Sdl3Backend()) {
            var window = backend.createWindow(WindowSpec.of("on the CPU", LogicalSize.of(120, 80)));
            assertEquals(
                    PresentMode.VSYNC, window.requestPresentMode(PresentMode.MAILBOX), "the surface's, as launched");
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
            assertEquals(new Presentation.Cpu("goldberry.gpu=off"), window.presentation(), "and says why");
            window.close();
        }
    }
}
