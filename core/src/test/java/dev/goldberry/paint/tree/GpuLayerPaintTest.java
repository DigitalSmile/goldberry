package dev.goldberry.paint.tree;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.ByteOrder;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import dev.goldberry.RendererRequirement;
import dev.goldberry.css.value.Transform;
import dev.goldberry.layout.FlexDirection;
import dev.goldberry.layout.Insets;
import dev.goldberry.layout.Length;
import dev.goldberry.layout.Overflow;
import dev.goldberry.layout.Position;
import dev.goldberry.paint.Box;
import dev.goldberry.paint.Frame;
import dev.goldberry.render.DamageRect;
import dev.goldberry.render.GpuContent;
import dev.goldberry.render.GpuPlacement;
import dev.goldberry.render.PixelBuffer;
import dev.goldberry.render.model.DisplayScale;
import dev.goldberry.render.model.PhysicalRect;
import dev.goldberry.render.model.PhysicalSize;
import dev.goldberry.render.model.PixelFormat;
import dev.goldberry.render.window.GpuSurface;

/// GPU layers placed by a render tree's walk, in paint order: scissored by the
/// tree's clips, not by the damage a partial repaint is confined to, and not
/// shown inside a group.
@DisplayName("a render tree placing GPU layers")
class GpuLayerPaintTest {

    private static final int GREY = 0xFF808080;
    private static final int FALLBACK = 0xFF00FF00;
    private static final GpuContent LAYER = new GpuContent() {};

    private PixelBuffer buffer;

    @BeforeEach
    void setUp() {
        RendererRequirement.enforce();
        buffer = PixelBuffer.allocate(new PhysicalSize(200, 200), PixelFormat.BGRA32_PREMULTIPLIED);
    }

    private Frame frame() {
        return Frame.over(buffer, DisplayScale.ONE, (GpuSurface.Composited) layers -> {});
    }

    private int pixel(int x, int y) {
        return buffer.pixels().duplicate().order(ByteOrder.LITTLE_ENDIAN).getInt(y * buffer.stride() + x * 4);
    }

    /// A box showing [#LAYER], or the fallback colour where it cannot.
    private static Box layer(double width, double height) {
        return Box.of()
                .size(Length.points((float) width), Length.points((float) height))
                .shrink(0)
                .painting((frame, size) -> {
                    if (!frame.gpuLayer(LAYER, 0, 0, size.width(), size.height())) {
                        frame.fillRect(0, 0, size.width(), size.height(), FALLBACK);
                    }
                });
    }

    /// A 100x100 clipping viewport at (20, 20) whose content is scrolled up by
    /// 30: a 40-tall row, then a 120-tall layer, which shows from 10 to 100.
    private static Box scrolled() {
        var content = Box.of()
                .direction(FlexDirection.COLUMN)
                .transform(
                        Transform.of(new Transform.Function.Translate(Transform.Length.ZERO, Transform.Length.px(-30))))
                .children(
                        Box.filled(GREY)
                                .size(Length.points(100), Length.points(40))
                                .shrink(0),
                        layer(100, 120));
        var viewport = Box.of()
                .size(Length.points(100), Length.points(100))
                .overflow(Overflow.HIDDEN)
                .position(Position.ABSOLUTE)
                .inset(new Insets(Length.points(20), Length.UNDEFINED, Length.UNDEFINED, Length.points(20)))
                .children(content);
        return Box.filled(GREY).size(Length.points(200), Length.points(200)).children(viewport);
    }

    @Test
    @DisplayName("scissors a layer scrolled partly out of a viewport to the viewport")
    void scrolledLayer() {
        var frame = frame();
        try (var tree = RenderTree.create()) {
            tree.update(frame, scrolled());
            tree.paint(frame);
        } finally {
            frame.end();
        }
        // The layer is at 20 + 40 - 30 = 30 from the top, 120 tall, and the
        // viewport ends at 120.
        assertEquals(
                List.of(new GpuPlacement(LAYER, PhysicalRect.of(20, 30, 100, 120), PhysicalRect.of(20, 30, 100, 90))),
                frame.gpuPlacements());
        assertEquals(0, pixel(50, 100), "a hole inside the viewport");
        assertEquals(GREY, pixel(50, 130), "and none below it");
    }

    @Test
    @DisplayName("scissors a layer by the tree's clips when the damage cuts across it, not by the damage")
    void damageIsNotAScissor() {
        try (var tree = RenderTree.create()) {
            var whole = frame();
            tree.update(whole, scrolled());
            tree.paint(whole);
            whole.end();

            var partial = frame();
            tree.update(partial, scrolled());
            tree.paint(partial, List.of(new DamageRect(40, 60, 10, 10)));
            partial.end();
            assertEquals(whole.gpuPlacements(), partial.gpuPlacements(), "placed as it was when painted whole");

            var elsewhere = frame();
            tree.update(elsewhere, scrolled());
            tree.paint(elsewhere, List.of(new DamageRect(150, 150, 10, 10)));
            elsewhere.end();
            assertTrue(elsewhere.gpuPlacements().isEmpty(), "damage that misses it does not reach it");
        }
    }

    @Test
    @DisplayName("cannot show a layer inside an opacity group, and paints the fallback there")
    void notInAGroup() {
        var group = Box.of()
                .opacity(0.5)
                .position(Position.ABSOLUTE)
                .inset(new Insets(Length.points(10), Length.UNDEFINED, Length.UNDEFINED, Length.points(10)))
                .children(layer(50, 50));
        var frame = frame();
        try (var tree = RenderTree.create()) {
            tree.update(
                    frame, Box.of().size(Length.points(200), Length.points(200)).children(group));
            tree.paint(frame);
        } finally {
            frame.end();
        }
        assertTrue(frame.gpuPlacements().isEmpty());
        assertEquals(0x80, pixel(30, 30) >>> 24, "the fallback, faded with its group");
    }
}
