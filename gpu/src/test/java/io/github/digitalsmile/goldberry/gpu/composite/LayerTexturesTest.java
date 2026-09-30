package io.github.digitalsmile.goldberry.gpu.composite;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import io.github.digitalsmile.goldberry.gpu.GpuDevice;
import io.github.digitalsmile.goldberry.gpu.GpuLayer;
import io.github.digitalsmile.goldberry.natives.sdl.Sdl;
import io.github.digitalsmile.goldberry.natives.sdl.gpu.GpuDeviceRequirement;
import io.github.digitalsmile.goldberry.natives.sdl.gpu.GpuTestLauncher;
import io.github.digitalsmile.goldberry.natives.sdl.gpu.SdlGpuDevice;
import io.github.digitalsmile.goldberry.render.GpuContent;
import io.github.digitalsmile.goldberry.render.GpuPlacement;
import io.github.digitalsmile.goldberry.render.PixelBuffer;
import io.github.digitalsmile.goldberry.render.model.PhysicalRect;
import io.github.digitalsmile.goldberry.render.model.PhysicalSize;

/// What a GPU layer renders into, and what reading one back gives (ADR-0481),
/// on a real device.
@Tag(GpuTestLauncher.TAG)
@DisplayName("GPU layers' textures and read-back")
class LayerTexturesTest {

    private static SdlGpuDevice required;
    private static SdlCompositor compositor;
    private static GpuDevice api;

    @BeforeAll
    static void createDevice() {
        required = GpuDeviceRequirement.enforce();
        compositor = new SdlCompositor();
        api = compositor.api().orElseThrow();
    }

    @AfterAll
    static void destroyDevice() {
        // Skipped before SDL was reached: nothing to give back, and no library to
        // call. Calling it anyway failed the class, and a build without it (ADR-0495).
        if (required == null) {
            return;
        }
        if (compositor != null) {
            compositor.close();
        }
        required.close();
        Sdl.get().quit();
    }

    private static GpuPlacement placed(GpuContent content, int width, int height) {
        var box = PhysicalRect.of(0, 0, width, height);
        return new GpuPlacement(content, box, box);
    }

    /// The pixel at `(x, y)`, as `0xAARRGGBB`.
    private static int pixel(PixelBuffer pixels, int x, int y) {
        return pixels.pixels()
                .duplicate()
                .order(java.nio.ByteOrder.LITTLE_ENDIAN)
                .getInt(y * pixels.stride() + x * 4);
    }

    @Nested
    @DisplayName("the textures")
    class Textures {

        @Test
        @DisplayName("are one per layer at its size, kept while it is placed and at that size")
        void keptAndRemade() {
            try (var textures = new LayerTextures()) {
                var layer = new TestLayer(0xFF0000FF, 0xFFFFFF00);
                var first = textures.renderAll(api, List.of(placed(layer, 16, 8)));
                var again = textures.renderAll(api, List.of(placed(layer, 16, 8)));
                assertSame(first.getFirst().texture(), again.getFirst().texture(), "kept at the same size");
                var resized = textures.renderAll(api, List.of(placed(layer, 20, 8)));
                assertNotSame(first.getFirst().texture(), resized.getFirst().texture(), "remade at another");
                assertEquals(20, resized.getFirst().texture().width());
                assertEquals(3, layer.renders(), "rendered on every frame it is placed on");
                assertEquals(1, textures.size());
                textures.renderAll(api, List.of());
                assertEquals(0, textures.size(), "released once it is not placed");
            }
        }

        @Test
        @DisplayName("show a still layer's last picture without rendering it, unless its texture is new")
        void stillLayers() {
            try (var textures = new LayerTextures()) {
                var layer = new TestLayer(0xFF0000FF, 0xFFFFFF00).still(true);
                textures.renderAll(api, List.of(placed(layer, 16, 8)));
                assertEquals(1, layer.renders(), "a new texture is rendered into, still or not");
                var again = textures.renderAll(api, List.of(placed(layer, 16, 8)));
                assertEquals(1, layer.renders(), "still: shown from what it rendered");
                assertEquals(1, again.size(), "and still drawn");
                textures.renderAll(api, List.of(placed(layer, 20, 8)));
                assertEquals(2, layer.renders(), "a new size is a new texture");
                layer.still(false);
                textures.renderAll(api, List.of(placed(layer, 20, 8)));
                assertEquals(3, layer.renders(), "changed");
            }
        }

        @Test
        @DisplayName("leave out what is not a layer, a layer that throws, and a second size of one layer")
        void leftOut() {
            try (var textures = new LayerTextures()) {
                var notALayer = new GpuContent() {};
                GpuLayer throwing = (frame, target) -> {
                    throw new IllegalStateException("a layer's own bug");
                };
                var layer = new TestLayer(0xFF0000FF, 0xFFFFFF00);
                var drawn = textures.renderAll(
                        api,
                        List.of(
                                placed(notALayer, 8, 8),
                                placed(throwing, 8, 8),
                                placed(layer, 8, 8),
                                placed(layer, 4, 4),
                                placed(layer, 8, 8)));
                assertEquals(2, drawn.size(), "the layer, twice at its first size");
                assertEquals(1, layer.renders(), "and rendered once");
                try (var frame = api.beginFrame()) {
                    assertNull(textures.render(frame, throwing, new PhysicalSize(8, 8)), "and it throws again");
                    frame.submit();
                }
            }
        }
    }

    @Nested
    @DisplayName("a read-back surface")
    class ReadBack {

        @Test
        @DisplayName("gives a layer's pixels, exactly, at the size asked for")
        void pixels() {
            try (var surface = compositor.readback()) {
                var layer = new TestLayer(0xFF0000FF, 0xFFFFFF00).moveTo(0.5, 0);
                var pixels = surface.render(layer, new PhysicalSize(8, 4)).orElseThrow();
                assertEquals(new PhysicalSize(8, 4), pixels.size());
                assertEquals(0xFF0000FF, pixel(pixels, 0, 0), "the background");
                assertEquals(0xFFFFFF00, pixel(pixels, 4, 0), "the square, two pixels on a side, at the middle");
                assertEquals(0xFFFFFF00, pixel(pixels, 5, 1));
                assertEquals(0xFF0000FF, pixel(pixels, 6, 2));
            }
        }

        @Test
        @DisplayName("gives nothing for content that is not a layer, once closed, or with no device")
        void nothing() {
            var layer = new TestLayer(0xFF0000FF, 0xFFFFFF00);
            var surface = compositor.readback();
            assertEquals(Optional.empty(), surface.render(new GpuContent() {}, new PhysicalSize(4, 4)));
            surface.close();
            assertEquals(Optional.empty(), surface.render(layer, new PhysicalSize(4, 4)));
            var closed = new SdlCompositor();
            closed.close();
            try (var unavailable = closed.readback()) {
                assertEquals(Optional.empty(), unavailable.render(layer, new PhysicalSize(4, 4)));
            }
            assertEquals(0, layer.renders());
        }

        @Test
        @DisplayName("releases a layer's texture once it is not placed")
        void releases() {
            try (var surface = compositor.readback()) {
                var layer = new TestLayer(0xFF0000FF, 0xFFFFFF00);
                surface.render(layer, new PhysicalSize(4, 4));
                var readback = (SdlReadbackSurface) surface;
                assertEquals(1, readback.textures().size());
                surface.placed(List.of(placed(layer, 4, 4)));
                assertEquals(1, readback.textures().size(), "still placed");
                surface.placed(List.of());
                assertEquals(0, readback.textures().size());
                assertTrue(surface.render(layer, new PhysicalSize(4, 4)).isPresent(), "and makes it again");
            }
        }
    }
}
