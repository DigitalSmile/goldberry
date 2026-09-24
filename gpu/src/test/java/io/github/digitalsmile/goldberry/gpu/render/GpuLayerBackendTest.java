package io.github.digitalsmile.goldberry.gpu.render;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import io.github.digitalsmile.goldberry.natives.sdl.Sdl;
import io.github.digitalsmile.goldberry.natives.sdl.gpu.GpuDeviceRequirement;
import io.github.digitalsmile.goldberry.natives.sdl.gpu.GpuTestLauncher;
import io.github.digitalsmile.goldberry.natives.sdl.gpu.SdlGpuDevice;
import io.github.digitalsmile.goldberry.paint.Frame;
import io.github.digitalsmile.goldberry.render.DamageRect;
import io.github.digitalsmile.goldberry.render.GpuPlacement;
import io.github.digitalsmile.goldberry.render.PixelBuffer;
import io.github.digitalsmile.goldberry.render.backend.headless.HeadlessBackend;
import io.github.digitalsmile.goldberry.render.backend.sdl3.Sdl3Backend;
import io.github.digitalsmile.goldberry.render.model.LogicalSize;
import io.github.digitalsmile.goldberry.render.model.PhysicalRect;
import io.github.digitalsmile.goldberry.render.model.PixelFormat;
import io.github.digitalsmile.goldberry.render.window.GpuSurface;
import io.github.digitalsmile.goldberry.render.window.WindowSpec;

/// GPU layers through the backends (ADR-0481): which surface a window gives,
/// and what it does with the layers a frame placed. `:gpu` is on this module's
/// path, so both backends find its compositor as an application's would.
@Tag(GpuTestLauncher.TAG)
@DisplayName("GPU layers, through the backends")
class GpuLayerBackendTest {

    private SdlGpuDevice required;

    @BeforeEach
    void video() {
        // SDL's video under the lane's driver, which every device here needs:
        // the headless backend initialises none of its own.
        required = GpuDeviceRequirement.enforce();
        var videoDriver = System.getProperty(GpuDeviceRequirement.VIDEO_DRIVER_PROPERTY, "");
        if (!videoDriver.isBlank()) {
            System.setProperty(Sdl3Backend.VIDEO_DRIVER_PROPERTY, videoDriver);
        }
    }

    @AfterEach
    void restore() {
        System.clearProperty("goldberry.gpu");
        System.clearProperty("goldberry.gpu.composite");
        System.clearProperty(Sdl3Backend.VIDEO_DRIVER_PROPERTY);
        if (required != null) {
            required.close();
        }
        Sdl.get().quit();
    }

    @Test
    @DisplayName("a headless window reads its layers back into its frame")
    void headlessReadsBack() {
        try (var backend = new HeadlessBackend()) {
            var window = backend.createWindow(WindowSpec.of("headless", LogicalSize.of(40, 30)));
            var surface = assertInstanceOf(
                    GpuSurface.ReadBack.class, window.gpuSurface().orElseThrow());
            var buffer = PixelBuffer.allocate(window.physicalSize(), PixelFormat.BGRA32_PREMULTIPLIED);
            var frame = Frame.over(buffer, window.scale(), surface);
            var layer = new TestLayer(0xFF0000FF, 0xFFFFFF00);
            assertTrue(frame.gpuLayer(layer, 10, 10, 20, 10), "shown");
            frame.end();
            surface.placed(frame.gpuPlacements());
            var pixels = buffer.pixels().duplicate().order(java.nio.ByteOrder.LITTLE_ENDIAN);
            assertEquals(0xFF0000FF, pixels.getInt((10 * 40 + 10) * 4), "its background, drawn into the frame");
            assertEquals(1, layer.renders());
            window.close();
        }
    }

    @Test
    @DisplayName("with the GPU off, a window has no surface, and a layer's painter paints its fallback")
    void gpuOff() {
        System.setProperty("goldberry.gpu", "off");
        try (var backend = new HeadlessBackend()) {
            var window = backend.createWindow(WindowSpec.of("off", LogicalSize.of(40, 30)));
            assertTrue(window.gpuSurface().isEmpty());
            window.close();
        }
        try (var backend = new Sdl3Backend()) {
            var window = backend.createWindow(WindowSpec.of("off", LogicalSize.of(40, 30)));
            window.acquireFrame();
            assertTrue(window.gpuSurface().isEmpty());
            window.close();
        }
    }

    @Test
    @DisplayName("a composited window draws its layers on every present, damaged or not")
    void compositedDrawsLayers() {
        try (var backend = new Sdl3Backend()) {
            var window = backend.createWindow(WindowSpec.of("composited", LogicalSize.of(64, 48)));
            assertTrue(window.acquireFrame().isEmpty(), "composited");
            var surface = assertInstanceOf(
                    GpuSurface.Composited.class, window.gpuSurface().orElseThrow());
            var size = window.physicalSize();
            var frame = PixelBuffer.allocate(size, PixelFormat.BGRA32_PREMULTIPLIED);
            var layer = new TestLayer(0xFF0000FF, 0xFFFFFF00);
            var box = PhysicalRect.of(4, 4, 16, 16);
            surface.placed(List.of(new GpuPlacement(layer, box, box)));
            window.present(frame, List.of(DamageRect.all(size)));
            assertTrue(window.lastPresent().composited());
            window.acquireFrame();
            window.present(frame, List.of());
            assertTrue(window.lastPresent().composited(), "composited again for its layer, with no damage");
            assertEquals(2, layer.renders());
            window.acquireFrame();
            surface.placed(List.of());
            window.present(frame, List.of());
            assertEquals(2, layer.renders(), "and not once it is gone");
            window.close();
        }
    }

    @Test
    @DisplayName("under auto, a window reads layers back until it has shown one, and is composited from then")
    void autoCompositesForLayers() {
        System.setProperty("goldberry.gpu.composite", "auto");
        try (var backend = new Sdl3Backend()) {
            var window = backend.createWindow(WindowSpec.of("auto", LogicalSize.of(64, 48)));
            var first = window.acquireFrame();
            assertTrue(first.isPresent(), "on the CPU with no layer");
            var readBack = assertInstanceOf(
                    GpuSurface.ReadBack.class, window.gpuSurface().orElseThrow());
            var layer = new TestLayer(0xFF0000FF, 0xFFFFFF00);
            var box = PhysicalRect.of(4, 4, 16, 16);
            readBack.placed(List.of(new GpuPlacement(layer, box, box)));
            window.present(first.get(), List.of(DamageRect.all(window.physicalSize())));

            assertTrue(window.acquireFrame().isEmpty(), "composited once a layer was shown");
            assertInstanceOf(GpuSurface.Composited.class, window.gpuSurface().orElseThrow());
            window.close();
        }
    }
}
