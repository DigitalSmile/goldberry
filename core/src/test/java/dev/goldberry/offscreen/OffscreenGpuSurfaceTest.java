package dev.goldberry.offscreen;

import static dev.goldberry.offscreen.Scene.BLUE;
import static dev.goldberry.offscreen.Scene.GREEN;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import dev.goldberry.RendererRequirement;
import dev.goldberry.css.ComputedStyle;
import dev.goldberry.layout.Length;
import dev.goldberry.paint.Box;
import dev.goldberry.render.GpuContent;
import dev.goldberry.render.GpuPlacement;
import dev.goldberry.render.PixelBuffer;
import dev.goldberry.render.model.PhysicalRect;
import dev.goldberry.render.model.PhysicalSize;
import dev.goldberry.render.model.PixelFormat;
import dev.goldberry.render.window.GpuSurface;
import dev.goldberry.widget.Widget;
import dev.goldberry.widget.style.Paints;

/// The GPU surface given to [Offscreen#gpu] shows GPU layers in every picture
/// the builder hands out: a still render, a strip's frames, and a session's.
///
/// No GPU is needed. The surface is a fake whose layers are one colour, so what
/// is asserted is that the layer's pixels are where it was placed and that the
/// surface heard about it. `:gpu`'s `OffscreenGpuTest` runs the same three with
/// a device.
///
/// Read more: [Testing an application](https://goldberry.dev/docs/guide/testing.html#pictures).
@DisplayName("an offscreen GPU surface")
class OffscreenGpuSurfaceTest {

    private static final GpuContent LAYER = new GpuContent() {};

    @BeforeEach
    void setUp() {
        RendererRequirement.enforce();
    }

    /// A read-back surface whose layers are all `argb`, keeping what each frame
    /// placed.
    private static final class Flat implements GpuSurface.ReadBack {
        private final int argb;
        final List<List<GpuPlacement>> placed = new ArrayList<>();

        Flat(int argb) {
            this.argb = argb;
        }

        @Override
        public Optional<PixelBuffer> render(GpuContent content, PhysicalSize size) {
            var bytes = ByteBuffer.allocate(size.width() * size.height() * 4).order(ByteOrder.LITTLE_ENDIAN);
            for (var i = 0; i < size.width() * size.height(); i++) {
                bytes.putInt(i * 4, argb);
            }
            return Optional.of(new PixelBuffer(size, PixelFormat.BGRA32_PREMULTIPLIED, size.width() * 4, bytes));
        }

        @Override
        public void placed(List<GpuPlacement> layers) {
            placed.add(List.copyOf(layers));
        }
    }

    /// A 40 by 30 window with a 20 by 10 GPU layer at (10, 5), over a blue
    /// background.
    private record Canvas() implements Widget.Leaf, Paints {

        @Override
        public Box render(ComputedStyle style, List<Box> children, Context context) {
            return Box.filled(BLUE)
                    .size(Length.points(40), Length.points(30))
                    .painting((frame, size) -> frame.gpuLayer(LAYER, 10, 5, 20, 10));
        }
    }

    private static final PhysicalRect BOX = PhysicalRect.of(10, 5, 20, 10);

    private static Offscreen over(GpuSurface surface) {
        return Offscreen.of(40, 30).gpu(surface);
    }

    @Test
    @DisplayName("in a still render")
    void render() {
        var surface = new Flat(GREEN);
        var picture = over(surface).render(new Canvas());
        assertEquals(GREEN, picture.argb(15, 8), "the layer");
        assertEquals(BLUE, picture.argb(2, 2), "the box around it");
        assertEquals(List.of(List.of(new GpuPlacement(LAYER, BOX, BOX))), surface.placed);
    }

    @Test
    @DisplayName("in each frame of a session, before and after the clock moves, and the surface hears of each")
    void session() {
        var surface = new Flat(GREEN);
        try (var session = over(surface).session(new Canvas())) {
            assertTrue(surface.placed.isEmpty(), "a pass that paints nothing places nothing");
            var first = session.frame();
            assertEquals(GREEN, first.argb(15, 8), "the layer");
            assertEquals(BLUE, first.argb(2, 2), "the box around it");

            session.advance(Duration.ofMillis(16));
            var second = session.frame();
            assertEquals(GREEN, second.argb(15, 8));
            assertEquals(
                    List.of(List.of(new GpuPlacement(LAYER, BOX, BOX)), List.of(new GpuPlacement(LAYER, BOX, BOX))),
                    surface.placed);
        }
    }

    @Test
    @DisplayName("in each frame of a strip")
    void strip() {
        var surface = new Flat(GREEN);
        try (var strip = over(surface).strip(new Canvas())) {
            assertEquals(GREEN, strip.frame().argb(15, 8));
            assertEquals(1, surface.placed.size());
        }
    }

    @Test
    @DisplayName("and without one, a session's GPU layer is not drawn and nothing is told")
    void without() {
        try (var session = Offscreen.of(40, 30).session(new Canvas())) {
            var frame = session.frame();
            assertEquals(BLUE, frame.argb(2, 2));
            assertNotEquals(GREEN, frame.argb(15, 8), "no GPU, no layer pixels");
        }
    }
}
