package dev.goldberry.paint;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import dev.goldberry.RendererRequirement;
import dev.goldberry.render.GpuContent;
import dev.goldberry.render.GpuPlacement;
import dev.goldberry.render.PixelBuffer;
import dev.goldberry.render.model.DisplayScale;
import dev.goldberry.render.model.PhysicalRect;
import dev.goldberry.render.model.PhysicalSize;
import dev.goldberry.render.model.PixelFormat;
import dev.goldberry.render.window.GpuSurface;

/// A frame placing GPU layers (`docs/gpu-plan.md`, D4; ADR-0481): where the hole
/// goes, what the scissor is, what a read-back layer's pixels replace, and what
/// the frame says when it has no GPU.
///
/// No GPU is needed: the surfaces here are fakes, which is the point of the
/// frame's side being `:core`'s. Whether the GPU draws what these record is
/// `:gpu`'s tests' question.
@DisplayName("a frame placing GPU layers")
class FrameGpuLayerTest {

    private static final int GREY = 0xFF808080;
    private static final int RED = 0xFFFF0000;
    private static final GpuContent LAYER = new GpuContent() {};

    @BeforeAll
    static void requireRenderer() {
        RendererRequirement.enforce();
    }

    /// A composited window's surface, keeping what it is told.
    private static final class RecordingComposited implements GpuSurface.Composited {
        List<GpuPlacement> placed = List.of();

        @Override
        public void placed(List<GpuPlacement> layers) {
            placed = layers;
        }
    }

    /// A read-back surface whose layers are one opaque colour, or none.
    private static final class FlatReadBack implements GpuSurface.ReadBack {
        final List<PhysicalSize> asked = new ArrayList<>();
        final int argb;
        final boolean fails;

        FlatReadBack(int argb, boolean fails) {
            this.argb = argb;
            this.fails = fails;
        }

        @Override
        public Optional<PixelBuffer> render(GpuContent content, PhysicalSize size) {
            asked.add(size);
            if (fails) {
                return Optional.empty();
            }
            // On the heap, as `:gpu`'s read-back gives it: the frame copies.
            var bytes = ByteBuffer.allocate(size.width() * size.height() * 4).order(ByteOrder.LITTLE_ENDIAN);
            for (var i = 0; i < size.width() * size.height(); i++) {
                bytes.putInt(i * 4, argb);
            }
            return Optional.of(new PixelBuffer(size, PixelFormat.BGRA32_PREMULTIPLIED, size.width() * 4, bytes));
        }

        @Override
        public void placed(List<GpuPlacement> layers) {}
    }

    private static final class Painted {
        final Frame frame;
        final PixelBuffer buffer;

        Painted(int width, int height, float scale, GpuSurface surface) {
            buffer = PixelBuffer.allocate(new PhysicalSize(width, height), PixelFormat.BGRA32_PREMULTIPLIED);
            frame = Frame.over(buffer, new DisplayScale(scale), surface);
        }

        int pixel(int x, int y) {
            return buffer.pixels().duplicate().order(ByteOrder.LITTLE_ENDIAN).getInt(y * buffer.stride() + x * 4);
        }
    }

    @Nested
    @DisplayName("over a composited window")
    class OverACompositedWindow {

        @Test
        @DisplayName("clears its box to transparent, exactly, and records it with the whole box as its scissor")
        void punchesAHole() {
            var painted = new Painted(40, 30, 1f, new RecordingComposited());
            assertTrue(painted.frame.hasGpu());
            painted.frame.fill(GREY);
            assertTrue(painted.frame.gpuLayer(LAYER, 10, 5, 20, 10));
            painted.frame.end();

            assertEquals(0, painted.pixel(10, 5), "the corner inside");
            assertEquals(0, painted.pixel(29, 14), "the far corner inside");
            assertEquals(GREY, painted.pixel(9, 5), "left of it");
            assertEquals(GREY, painted.pixel(30, 14), "right of it");
            assertEquals(GREY, painted.pixel(10, 15), "below it");
            var box = PhysicalRect.of(10, 5, 20, 10);
            assertEquals(List.of(new GpuPlacement(LAYER, box, box)), painted.frame.gpuPlacements());
        }

        @Test
        @DisplayName("is under what is painted after it, which is what makes paint order the z-order")
        void laterPaintIsAbove() {
            var painted = new Painted(40, 30, 1f, new RecordingComposited());
            painted.frame.fill(GREY);
            painted.frame.gpuLayer(LAYER, 0, 0, 20, 20);
            painted.frame.fillRect(10, 10, 20, 20, RED);
            painted.frame.end();

            assertEquals(0, painted.pixel(5, 5), "the hole where nothing is over it");
            assertEquals(RED, painted.pixel(15, 15), "what came after, over the hole");
        }

        @Test
        @DisplayName("is placed in physical pixels, through the transform, each edge rounded to the nearest")
        void physicalPixels() {
            var painted = new Painted(100, 100, 1.5f, new RecordingComposited());
            painted.frame.transform(1, 0, 0, 1, 10, 4);
            // (1, 1) + (10, 4) is (11, 5) logical: 16.5 and 7.5 physical, which
            // round to 17 and 8; the far edges, 31 and 15 logical, are 46.5 and
            // 22.5, and round to 47 and 23.
            painted.frame.gpuLayer(LAYER, 1, 1, 20, 10);
            painted.frame.end();

            var box = PhysicalRect.of(17, 8, 30, 15);
            assertEquals(box, painted.frame.gpuPlacements().getFirst().target());
            assertEquals(0, painted.pixel(17, 8));
            assertEquals(0, painted.pixel(46, 22));
        }

        @Test
        @DisplayName("under a rotation, covers the bounding box: layers are axis-aligned")
        void boundingBoxUnderARotation() {
            var painted = new Painted(100, 100, 1f, new RecordingComposited());
            // A quarter turn about the origin, then into view: (x, y) -> (60 - y, x).
            painted.frame.transform(0, 1, -1, 0, 60, 0);
            painted.frame.gpuLayer(LAYER, 0, 0, 20, 10);
            painted.frame.end();

            assertEquals(
                    PhysicalRect.of(50, 0, 10, 20),
                    painted.frame.gpuPlacements().getFirst().target());
        }

        @Test
        @DisplayName("is scissored by the clips in force, and back to whole once they are gone")
        void scissoredByClips() {
            var painted = new Painted(100, 100, 1f, new RecordingComposited());
            painted.frame.fill(GREY);
            painted.frame.save();
            painted.frame.clipTo(0, 0, 50, 50);
            painted.frame.clipTo(20, 0, 80, 40);
            painted.frame.gpuLayer(LAYER, 10, 10, 60, 60);
            painted.frame.restore();
            painted.frame.gpuLayer(LAYER, 10, 10, 60, 60);
            painted.frame.clipTo(0, 0, 30, 30);
            painted.frame.resetClip();
            painted.frame.gpuLayer(LAYER, 10, 10, 60, 60);
            painted.frame.end();

            var placements = painted.frame.gpuPlacements();
            var box = PhysicalRect.of(10, 10, 60, 60);
            assertEquals(PhysicalRect.of(20, 10, 30, 30), placements.get(0).scissor(), "the two clips' overlap");
            assertEquals(box, placements.get(1).scissor(), "restored");
            assertEquals(box, placements.get(2).scissor(), "reset");
            assertEquals(0, painted.pixel(20, 10), "the hole inside the clips");
        }

        @Test
        @DisplayName("is cut to the frame, and one the clips hide entirely is neither placed nor punched")
        void hiddenIsNotPlaced() {
            var painted = new Painted(40, 30, 1f, new RecordingComposited());
            painted.frame.fill(GREY);
            painted.frame.gpuLayer(LAYER, 30, 20, 50, 50);
            painted.frame.clipTo(0, 0, 10, 10);
            assertTrue(painted.frame.gpuLayer(LAYER, 20, 20, 5, 5), "nothing to show, and nothing the painter must do");
            painted.frame.end();

            var placements = painted.frame.gpuPlacements();
            assertEquals(1, placements.size(), "only the first");
            assertEquals(PhysicalRect.of(30, 20, 50, 50), placements.getFirst().target(), "reaching past the frame");
            assertEquals(PhysicalRect.of(30, 20, 10, 10), placements.getFirst().scissor(), "cut to it");
            assertEquals(GREY, painted.pixel(22, 22), "the hidden one punched nothing");
        }

        @Test
        @DisplayName("an empty box shows nothing and needs no fallback")
        void emptyBox() {
            var painted = new Painted(20, 20, 1f, new RecordingComposited());
            assertTrue(painted.frame.gpuLayer(LAYER, 5, 5, 0, 10));
            assertTrue(painted.frame.gpuLayer(LAYER, 5, 5, 0.2, 0.2), "less than a pixel rounds to nothing");
            painted.frame.end();
            assertTrue(painted.frame.gpuPlacements().isEmpty());
        }
    }

    @Nested
    @DisplayName("repainting only a region")
    class RepaintingARegion {

        @Test
        @DisplayName("punches only inside the region, and scissors the layer by its clips, not by the region")
        void regionIsNotAScissor() {
            var painted = new Painted(100, 100, 1f, new RecordingComposited());
            painted.frame.fill(GREY);
            painted.frame.repaintOnly(0, 0, 30, 100, () -> painted.frame.gpuLayer(LAYER, 10, 10, 60, 60));
            painted.frame.end();

            var box = PhysicalRect.of(10, 10, 60, 60);
            assertEquals(
                    new GpuPlacement(LAYER, box, box),
                    painted.frame.gpuPlacements().getFirst());
            assertEquals(0, painted.pixel(20, 20), "inside the region: the hole");
            assertEquals(GREY, painted.pixel(40, 20), "outside it: last frame's pixels, left alone");
        }

        @Test
        @DisplayName("a reset inside it goes back to the region, and its end puts the clip back")
        void resetStaysInTheRegion() {
            var painted = new Painted(100, 100, 1f, new RecordingComposited());
            painted.frame.clipTo(0, 0, 80, 80);
            painted.frame.repaintOnly(0, 0, 50, 50, () -> {
                painted.frame.clipTo(0, 0, 20, 20);
                painted.frame.resetClip();
                painted.frame.fillRect(0, 0, 100, 100, RED);
                painted.frame.gpuLayer(LAYER, 0, 0, 100, 100);
            });
            painted.frame.fillRect(0, 0, 100, 100, GREY);
            painted.frame.end();

            assertEquals(
                    PhysicalRect.of(0, 0, 80, 80),
                    painted.frame.gpuPlacements().getFirst().scissor(),
                    "the clip from before the region, and not the region");
            assertEquals(GREY, painted.pixel(60, 60), "the clip from before is back afterwards");
            assertEquals(0, painted.pixel(90, 90), "and still in force");
        }

        @Test
        @DisplayName("does not nest, and a region of no area paints nothing")
        void rules() {
            var painted = new Painted(20, 20, 1f, new RecordingComposited());
            var ran = new boolean[1];
            painted.frame.repaintOnly(0, 0, 0, 10, () -> ran[0] = true);
            assertFalse(ran[0], "nothing to repaint");
            painted.frame.repaintOnly(
                    0,
                    0,
                    10,
                    10,
                    () -> assertThrows(
                            IllegalStateException.class, () -> painted.frame.repaintOnly(0, 0, 5, 5, () -> {})));
            painted.frame.end();
        }
    }

    @Nested
    @DisplayName("over a read-back surface")
    class OverAReadBackSurface {

        @Test
        @DisplayName("replaces its box with the layer's pixels, rendered at the box's physical size")
        void drawsThePixels() {
            var readBack = new FlatReadBack(0xFF00FF00, false);
            var painted = new Painted(80, 60, 2f, readBack);
            painted.frame.fill(0x80000000);
            assertTrue(painted.frame.gpuLayer(LAYER, 5, 5, 10, 8));
            painted.frame.fillRect(10, 5, 10, 8, RED);
            painted.frame.end();

            assertEquals(List.of(new PhysicalSize(20, 16)), readBack.asked);
            assertEquals(0xFF00FF00, painted.pixel(10, 10), "replaced, not blended over the translucent fill");
            assertEquals(0xFF00FF00, painted.pixel(19, 25), "to the far corner");
            assertEquals(0x80000000, painted.pixel(9, 10), "and nothing outside it");
            assertEquals(RED, painted.pixel(25, 12), "under what came after");
            assertEquals(
                    PhysicalRect.of(10, 10, 20, 16),
                    painted.frame.gpuPlacements().getFirst().target());
        }

        @Test
        @DisplayName("says it cannot when the layer does not come back, and places nothing")
        void failureIsAFallback() {
            var painted = new Painted(20, 20, 1f, new FlatReadBack(0, true));
            painted.frame.fill(GREY);
            assertFalse(painted.frame.gpuLayer(LAYER, 0, 0, 10, 10));
            painted.frame.end();
            assertTrue(painted.frame.gpuPlacements().isEmpty());
            assertEquals(GREY, painted.pixel(5, 5));
        }

        @Test
        @DisplayName("renders nothing for a layer outside the region being repainted, and still places it")
        void outsideTheRegionIsNotRendered() {
            var readBack = new FlatReadBack(0xFF00FF00, false);
            var painted = new Painted(100, 100, 1f, readBack);
            painted.frame.repaintOnly(0, 0, 20, 20, () -> painted.frame.gpuLayer(LAYER, 50, 50, 10, 10));
            painted.frame.end();
            assertTrue(readBack.asked.isEmpty(), "its pixels are last frame's, already there");
            assertEquals(1, painted.frame.gpuPlacements().size(), "and it is still on screen");
        }
    }

    @Test
    @DisplayName("without a surface, says so and draws nothing")
    void noSurface() {
        var target = TestFrames.of(20, 20, 1f);
        target.frame().fill(GREY);
        assertFalse(target.frame().hasGpu());
        assertFalse(target.frame().gpuLayer(LAYER, 0, 0, 10, 10));
        target.end();
        assertEquals(GREY, target.pixel(5, 5));
        assertTrue(target.frame().gpuPlacements().isEmpty());
    }

    @Test
    @DisplayName("refuses a coordinate that is not finite")
    void refusesNonFinite() {
        var painted = new Painted(20, 20, 1f, new RecordingComposited());
        assertThrows(IllegalArgumentException.class, () -> painted.frame.gpuLayer(LAYER, Double.NaN, 0, 10, 10));
        assertThrows(
                IllegalArgumentException.class,
                () -> painted.frame.gpuLayer(LAYER, 0, 0, Double.POSITIVE_INFINITY, 10));
        painted.frame.end();
    }
}
