package dev.goldberry.gpu.render.mapped;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import dev.goldberry.gpu.render.staging.UploadAlignment;

/// How a raster painted in place is laid out per driver: what phase 0
/// measured as each driver's fastest.
@DisplayName("an in-place raster's layout per driver")
class InPlaceLayoutTest {

    @Test
    @DisplayName("Vulkan packs its rows and copies rectangles")
    void vulkan() {
        var layout = InPlaceLayout.of("vulkan", true);
        assertEquals(new InPlaceLayout(UploadAlignment.PACKED, CopyShape.RECTANGLES), layout);
        assertEquals(1001 * 4, layout.stride(1001));
    }

    @Test
    @DisplayName("Metal packs its rows, so a band is one command, and copies bands")
    void metal() {
        var layout = InPlaceLayout.of("metal", false);
        assertEquals(new InPlaceLayout(UploadAlignment.PACKED, CopyShape.BANDS), layout);
        assertEquals(1001 * 4, layout.stride(1001));
    }

    @Test
    @DisplayName("Direct3D 12 pads its rows to 256 bytes and copies bands")
    void direct3d12() {
        var layout = InPlaceLayout.of("direct3d12", true);
        assertEquals(new InPlaceLayout(UploadAlignment.DIRECT3D12, CopyShape.BANDS), layout);
        assertEquals(4096, layout.stride(1001));
        assertEquals(7680, layout.stride(1920), "a 1080p row is already a multiple of 256");
    }
}
