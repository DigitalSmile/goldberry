package dev.goldberry.paint.filter;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.ByteOrder;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import dev.goldberry.css.compositing.FilterFunction;
import dev.goldberry.render.PixelBuffer;
import dev.goldberry.render.model.PhysicalSize;
import dev.goldberry.render.model.PixelFormat;

/// A filter run as if the raster went on past its edges, each pixel there a
/// copy of the nearest one on the edge: what a `backdrop-filter` reads at the
/// window's edge.
@DisplayName("filters over extended edges")
class FiltersEdgeTest {

    private static final int RED = 0xFFFF0000;

    private static PixelBuffer filled(int width, int height, int argb) {
        var buffer = PixelBuffer.allocate(new PhysicalSize(width, height), PixelFormat.BGRA32_PREMULTIPLIED);
        var bytes = buffer.pixels().duplicate().order(ByteOrder.LITTLE_ENDIAN);
        for (var y = 0; y < height; y++) {
            for (var x = 0; x < width; x++) {
                bytes.putInt(y * buffer.stride() + x * 4, argb);
            }
        }
        return buffer;
    }

    private static int pixel(PixelBuffer buffer, int x, int y) {
        return buffer.pixels().duplicate().order(ByteOrder.LITTLE_ENDIAN).getInt(y * buffer.stride() + x * 4);
    }

    @Test
    @DisplayName("a blur of a flat colour stays that colour up to an extended edge")
    void flatStaysFlat() {
        var buffer = filled(20, 20, RED);
        Filters.apply(buffer, List.of(new FilterFunction.Blur(3)), 1, new EdgeExtension(10, 10, 10, 10));
        assertEquals(RED, pixel(buffer, 0, 0));
        assertEquals(RED, pixel(buffer, 19, 10));
    }

    @Test
    @DisplayName("and fades into the transparent outside at an edge that is not")
    void fadesWithout() {
        var buffer = filled(20, 20, RED);
        Filters.apply(buffer, List.of(new FilterFunction.Blur(3)), 1, EdgeExtension.NONE);
        assertTrue(pixel(buffer, 0, 10) >>> 24 < 0xFF, "the left edge lost some alpha");
    }

    @Test
    @DisplayName("extends only the sides it is asked to")
    void oneSide() {
        var buffer = filled(20, 20, RED);
        Filters.apply(buffer, List.of(new FilterFunction.Blur(3)), 1, new EdgeExtension(10, 0, 0, 0));
        assertEquals(RED, pixel(buffer, 0, 10), "the extended left edge");
        assertTrue(pixel(buffer, 19, 10) >>> 24 < 0xFF, "the right edge, not extended");
    }

    @Test
    @DisplayName("refuses a negative extension")
    void negative() {
        assertThrows(IllegalArgumentException.class, () -> new EdgeExtension(-1, 0, 0, 0));
    }
}
