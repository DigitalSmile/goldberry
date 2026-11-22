package dev.goldberry.gpu.render.mapped;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import dev.goldberry.gpu.render.staging.UploadAlignment;
import dev.goldberry.natives.sdl.gpu.SdlGpuRegion;

/// What is copied out of a frame-sized raster for a frame's damage, per shape.
@DisplayName("what an in-place raster copies")
class CopyShapeTest {

    private static final List<SdlGpuRegion> DAMAGE =
            List.of(new SdlGpuRegion(100, 41, 30, 9), new SdlGpuRegion(7, 3, 20, 5), new SdlGpuRegion(50, 45, 10, 20));

    @Test
    @DisplayName("rectangles are the damage as it came")
    void rectangles() {
        assertEquals(DAMAGE, CopyShape.RECTANGLES.copies(DAMAGE, 200, 800, UploadAlignment.PACKED));
    }

    @Nested
    @DisplayName("bands")
    class Bands {

        @Test
        @DisplayName("are whole rows, sorted, with overlapping and touching rows joined")
        void joined() {
            var bands = CopyShape.BANDS.copies(DAMAGE, 200, 800, UploadAlignment.PACKED);
            assertEquals(List.of(new SdlGpuRegion(0, 3, 200, 5), new SdlGpuRegion(0, 41, 200, 24)), bands);
            var touching = CopyShape.BANDS.copies(
                    List.of(new SdlGpuRegion(0, 0, 5, 10), new SdlGpuRegion(9, 10, 5, 10)),
                    200,
                    800,
                    UploadAlignment.PACKED);
            assertEquals(List.of(new SdlGpuRegion(0, 0, 200, 20)), touching);
        }

        @Test
        @DisplayName("on Direct3D 12 start on rows at multiples of 512 bytes when the stride is an odd multiple of 256")
        void evenRowsOnDirect3d12() {
            // 64 texels is 256 bytes a row: every other row starts at a
            // multiple of 512.
            var bands = CopyShape.BANDS.copies(
                    List.of(new SdlGpuRegion(3, 7, 4, 2), new SdlGpuRegion(3, 20, 4, 3)),
                    64,
                    256,
                    UploadAlignment.DIRECT3D12);
            assertEquals(List.of(new SdlGpuRegion(0, 6, 64, 3), new SdlGpuRegion(0, 20, 64, 3)), bands);
        }

        @Test
        @DisplayName("for any damage cover it, at offsets Direct3D 12 copies from as they are, and never overlap")
        void anyDamage() {
            var random = new Random(31);
            for (var round = 0; round < 300; round++) {
                var width = 1 + random.nextInt(700);
                var stride = UploadAlignment.DIRECT3D12.pitch(width * 4L);
                var damage = new ArrayList<SdlGpuRegion>();
                for (var i = 0; i < 1 + random.nextInt(8); i++) {
                    var x = random.nextInt(width);
                    damage.add(new SdlGpuRegion(
                            x, random.nextInt(400), 1 + random.nextInt(width - x), 1 + random.nextInt(60)));
                }
                var bands = CopyShape.BANDS.copies(damage, width, stride, UploadAlignment.DIRECT3D12);
                var lastBottom = -1;
                for (var band : bands) {
                    assertEquals(0, band.x(), band::toString);
                    assertEquals(width, band.width(), band::toString);
                    assertEquals(0, (long) band.y() * stride % 512, () -> band + " at stride " + stride);
                    assertTrue(band.y() > lastBottom, () -> band + " overlaps or touches the band above");
                    lastBottom = band.y() + band.height();
                }
                for (var region : damage) {
                    assertTrue(
                            bands.stream()
                                    .anyMatch(band -> band.y() <= region.y()
                                            && band.y() + band.height() >= region.y() + region.height()),
                            () -> region + " is not inside one of " + bands);
                }
            }
        }
    }

    @Test
    @DisplayName("refuses damage wider than the frame")
    void refusesWiderDamage() {
        assertThrows(
                IllegalArgumentException.class,
                () -> CopyShape.BANDS.copies(
                        List.of(new SdlGpuRegion(190, 0, 20, 1)), 200, 800, UploadAlignment.PACKED));
    }
}
