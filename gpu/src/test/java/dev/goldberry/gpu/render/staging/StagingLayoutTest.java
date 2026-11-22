package dev.goldberry.gpu.render.staging;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import dev.goldberry.natives.sdl.gpu.SdlGpuRegion;

/// Where an upload's regions land in staging memory: packed as before on most
/// drivers, and on Direct3D 12 as it copies without copying again first.
@DisplayName("the layout of staged regions")
class StagingLayoutTest {

    private static final List<SdlGpuRegion> DAMAGE =
            List.of(new SdlGpuRegion(3, 5, 41, 7), new SdlGpuRegion(0, 0, 1, 1), new SdlGpuRegion(100, 20, 64, 3));

    @Test
    @DisplayName("packed, each region's rows follow one another and the regions do too, as before")
    void packed() {
        var layout = StagingLayout.of(UploadAlignment.PACKED, 4, DAMAGE);
        var regions = layout.regions();
        assertEquals(new StagedRegion(DAMAGE.get(0), 0, 41 * 4), regions.get(0));
        assertEquals(new StagedRegion(DAMAGE.get(1), 41 * 4 * 7, 4), regions.get(1));
        assertEquals(new StagedRegion(DAMAGE.get(2), 41 * 4 * 7 + 4, 64 * 4), regions.get(2));
        assertEquals(41 * 4 * 7 + 4 + 64 * 4 * 3, layout.size());
    }

    @Test
    @DisplayName("on Direct3D 12, rows are their packed length rounded up to 256 and regions start at multiples of 512")
    void direct3d12() {
        var layout = StagingLayout.of(UploadAlignment.DIRECT3D12, 4, DAMAGE);
        var regions = layout.regions();
        assertEquals(new StagedRegion(DAMAGE.get(0), 0, 256), regions.get(0));
        assertEquals(new StagedRegion(DAMAGE.get(1), 2048, 256), regions.get(1), "after 7 rows of 256, at 1792");
        assertEquals(new StagedRegion(DAMAGE.get(2), 2560, 256), regions.get(2), "after one row, at 2304");
        assertEquals(2560 + 256 * 3, layout.size());
    }

    @Test
    @DisplayName("a whole 1080p or 4K frame takes no padding: its rows are already multiples of 256")
    void wholeFramesAreNotPadded() {
        for (var width : new int[] {1920, 3840}) {
            var frame = new SdlGpuRegion(0, 0, width, width * 9 / 16);
            var layout = StagingLayout.of(UploadAlignment.DIRECT3D12, 4, List.of(frame));
            assertEquals(new StagedRegion(frame, 0, width * 4), layout.regions().getFirst());
            assertEquals((long) width * 4 * frame.height(), layout.size());
        }
    }

    @Test
    @DisplayName("for any damage, every region is aligned, fits, and overlaps no other")
    void anyDamage() {
        var random = new Random(30);
        for (var round = 0; round < 200; round++) {
            var damage = new ArrayList<SdlGpuRegion>();
            for (var i = 0; i < 1 + random.nextInt(12); i++) {
                damage.add(new SdlGpuRegion(
                        random.nextInt(500), random.nextInt(500), 1 + random.nextInt(700), 1 + random.nextInt(90)));
            }
            var bytesPerPixel = random.nextBoolean() ? 4 : 8;
            var layout = StagingLayout.of(UploadAlignment.DIRECT3D12, bytesPerPixel, damage);
            var end = 0L;
            for (var placed : layout.regions()) {
                var packed = placed.region().width() * bytesPerPixel;
                assertEquals(0, placed.offset() % 512, placed::toString);
                assertEquals(0, placed.rowBytes() % 256, placed::toString);
                assertTrue(placed.rowBytes() >= packed && placed.rowBytes() < packed + 256, placed::toString);
                assertTrue(placed.offset() >= end, () -> placed + " overlaps the region before it");
                end = placed.offset() + placed.bytes();
            }
            assertEquals(end, layout.size());
        }
    }

    @Test
    @DisplayName("gives SDL the pitch in texels, and refuses a pitch that is not whole texels")
    void pixelsPerRow() {
        var placed = new StagedRegion(new SdlGpuRegion(0, 0, 41, 1), 0, 256);
        assertEquals(64, placed.pixelsPerRow(4));
        assertEquals(32, placed.pixelsPerRow(8));
        assertThrows(IllegalArgumentException.class, () -> placed.pixelsPerRow(3));
    }

    @Test
    @DisplayName("refuses a layout past SDL's 2 GiB")
    void capsAtSdlsLimit() {
        var huge = new SdlGpuRegion(0, 0, 16384, 16384);
        assertThrows(
                IllegalArgumentException.class,
                () -> StagingLayout.of(UploadAlignment.DIRECT3D12, 16, List.of(huge)),
                "16384 × 16384 × 16 bytes is 4 GiB");
    }
}
