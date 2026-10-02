package dev.goldberry.gpu.view;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;

import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import dev.goldberry.gpu.GpuDevice;
import dev.goldberry.gpu.GpuFrame;
import dev.goldberry.gpu.GpuTexture;
import dev.goldberry.gpu.Load;
import dev.goldberry.gpu.TextureFormat;
import dev.goldberry.gpu.TextureSpec;
import dev.goldberry.gpu.composite.CompositeHarness;
import dev.goldberry.natives.sdl.gpu.GpuTestLauncher;
import dev.goldberry.render.model.PhysicalSize;

/// A `canvas3d`'s layer driving its renderer through the renderer's lifecycle,
/// on a real device. Read more: [canvas3d](https://goldberry.dev/docs/components/gpu.html#canvas3d).
@Tag(GpuTestLauncher.TAG)
@DisplayName("a canvas3d's layer")
class Canvas3dLayerTest {

    private static CompositeHarness harness;
    private static GpuDevice device;

    @BeforeAll
    static void open() {
        harness = CompositeHarness.open();
        device = harness.device();
    }

    @AfterAll
    static void close() {
        if (harness != null) {
            harness.close();
        }
    }

    /// A renderer that clears and records what it was asked, and what it got.
    private static final class Recording implements Canvas3dRenderer {
        final List<String> calls = new ArrayList<>();

        @Nullable
        Canvas3dTarget last;

        @Override
        public void init(GpuDevice device) {
            calls.add("init");
        }

        @Override
        public void resize(PhysicalSize size) {
            calls.add("resize " + size.width() + "x" + size.height());
        }

        @Override
        public void render(GpuFrame frame, Canvas3dTarget target) {
            calls.add("render");
            last = target;
            frame.renderPass(target.colour(), Load.clear(0, 0, 0, 1), pass -> {});
        }

        @Override
        public void dispose() {
            calls.add("dispose");
        }
    }

    private static void render(GpuDevice on, Canvas3dLayer layer, int width, int height) {
        try (var target = on.createTexture(TextureSpec.renderTarget(TextureFormat.B8G8R8A8_UNORM, width, height));
                var frame = on.beginFrame()) {
            layer.render(frame, target);
            frame.submit();
        }
    }

    @Test
    @DisplayName("initialises and sizes the renderer before its first frame, and resizes it when the size changes")
    void lifecycle() {
        var renderer = new Recording();
        try (var layer = new Canvas3dLayer(renderer, null)) {
            layer.frame(1_500_000_000L, false);
            render(device, layer, 32, 16);
            render(device, layer, 32, 16);
            render(device, layer, 40, 20);
            assertEquals(List.of("init", "resize 32x16", "render", "render", "resize 40x20", "render"), renderer.calls);
            var target = renderer.last;
            assertNotNull(target);
            assertNull(target.depth(), "none was asked for");
            assertEquals(1.5, target.seconds(), 1e-9, "the frame's time");
            assertEquals(2.0, target.aspect(), 1e-9);
            assertThrows(IllegalStateException.class, target::clearDepth);
        }
        assertEquals("dispose", renderer.calls.getLast(), "disposed when the canvas goes");
    }

    @Test
    @DisplayName("gives the renderer a depth target at its size, remade when the size changes")
    void depth() {
        var renderer = new Recording();
        try (var layer = new Canvas3dLayer(renderer, TextureFormat.D16_UNORM)) {
            render(device, layer, 24, 12);
            GpuTexture first = renderer.last == null ? null : renderer.last.depth();
            assertNotNull(first);
            assertEquals(TextureFormat.D16_UNORM, first.format());
            assertEquals(24, first.width());
            render(device, layer, 30, 10);
            var second = renderer.last == null ? null : renderer.last.depth();
            assertNotNull(second);
            assertEquals(30, second.width());
            assertTrue(first.isClosed(), "the old one released");
        }
        assertThrows(IllegalArgumentException.class, () -> new Canvas3dLayer(new Recording(), TextureFormat.R8_UNORM));
    }

    @Test
    @DisplayName("needs rendering on demand once, again when a redraw is asked, and always when continuous")
    void onDemand() {
        try (var layer = new Canvas3dLayer(new Recording(), null)) {
            assertTrue(layer.needsRender(), "never drawn");
            render(device, layer, 8, 8);
            assertFalse(layer.needsRender(), "drawn, and nothing asked since");
            layer.redraw();
            assertTrue(layer.needsRender());
            render(device, layer, 8, 8);
            assertFalse(layer.needsRender());
            layer.frame(0, true);
            assertTrue(layer.needsRender(), "continuous");
            render(device, layer, 8, 8);
            assertTrue(layer.needsRender(), "still continuous");
        }
    }

    @Test
    @DisplayName("disposes the renderer and initialises it again on a new device, and not at all once closed")
    void newDevice() {
        var renderer = new Recording();
        var layer = new Canvas3dLayer(renderer, TextureFormat.D16_UNORM);
        render(device, layer, 8, 8);
        var other = harness.otherDevice();
        render(other, layer, 8, 8);
        assertEquals(
                List.of("init", "resize 8x8", "render", "dispose", "init", "resize 8x8", "render"), renderer.calls);
        harness.closeOthers();
        layer.close();
        layer.close();
        assertEquals("dispose", renderer.calls.getLast());
        assertEquals(8, renderer.calls.size(), "disposed once, though its device had gone");
        assertFalse(layer.needsRender(), "closed");
        assertThrows(IllegalStateException.class, () -> render(device, layer, 8, 8));
    }
}
