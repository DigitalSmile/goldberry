package dev.goldberry.gpu.video;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.ByteBuffer;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import dev.goldberry.gpu.TextureFormat;
import dev.goldberry.render.model.PhysicalRect;

/// [VideoImage] and what a [VideoLayer] accepts before it has a device: the
/// rules that need no GPU (`docs/gpu-plan.md`, phase 6).
@DisplayName("VideoImage, and a VideoLayer before its first render")
class VideoImageTest {

    private static VideoImage.Planes nv12(int width, int height) {
        return new VideoImage.Planes(
                PlaneLayout.NV12,
                width,
                height,
                List.of(ByteBuffer.allocateDirect(width * height), ByteBuffer.allocateDirect(width * height / 2)),
                List.of(width, width),
                ColorMatrix.BT709,
                false);
    }

    @Test
    @DisplayName("each layout names its planes' textures, luma first")
    void layouts() {
        assertEquals(List.of(TextureFormat.R8_UNORM, TextureFormat.R8G8_UNORM), PlaneLayout.NV12.textureFormats());
        assertEquals(3, PlaneLayout.I420.planes());
        assertEquals(List.of(TextureFormat.R16_UNORM, TextureFormat.R16G16_UNORM), PlaneLayout.P010.textureFormats());
        assertEquals(TextureFormat.R16_UNORM, PlaneLayout.I010.textureFormats().get(2));
        // Chroma rounds up for an odd size.
        assertEquals(3, PlaneLayout.I420.planeWidth(1, 5));
        assertEquals(2, PlaneLayout.I420.planeHeight(1, 3));
        for (var layout : PlaneLayout.values()) {
            assertEquals(layout.name(), layout.yuv().name());
        }
        for (var matrix : ColorMatrix.values()) {
            assertEquals(matrix.name(), matrix.yuv().name());
        }
    }

    @Test
    @DisplayName("planes are checked against their layout")
    void planesAreChecked() {
        var planes = nv12(4, 2);
        assertEquals(PlaneLayout.NV12, planes.layout());
        assertEquals(List.of(4, 4), planes.strides());
        assertTrue(planes.toString().contains("NV12 4×2 BT709 limited"));
        var luma = ByteBuffer.allocateDirect(8);
        assertThrows(
                IllegalArgumentException.class,
                () -> new VideoImage.Planes(
                        PlaneLayout.I420, 4, 2, List.of(luma, luma), List.of(4, 2), ColorMatrix.BT601, false),
                "I420 has three planes");
        assertThrows(
                IllegalArgumentException.class,
                () -> new VideoImage.Planes(
                        PlaneLayout.P010, 4, 2, List.of(luma, luma), List.of(4, 8), ColorMatrix.BT601, false),
                "a 16-bit luma row of four pixels is eight bytes");
        assertThrows(
                IllegalArgumentException.class,
                () -> new VideoImage.Planes(
                        PlaneLayout.NV12, 0, 2, List.of(luma, luma), List.of(4, 4), ColorMatrix.BT601, false));
    }

    @Test
    @DisplayName("BGRA is checked for a stride that holds a row")
    void bgraIsChecked() {
        var pixels = ByteBuffer.allocateDirect(32);
        var bgra = new VideoImage.Bgra(4, 2, 16, pixels);
        assertSame(pixels, bgra.pixels());
        assertEquals(16, bgra.stride());
        assertThrows(IllegalArgumentException.class, () -> new VideoImage.Bgra(4, 2, 15, pixels));
        assertThrows(IllegalArgumentException.class, () -> new VideoImage.Bgra(4, -2, 16, pixels));
    }

    @Test
    @DisplayName("a layer needs a render when its picture or its part changes, and not for the same again")
    void needsRender() {
        try (var layer = new VideoLayer()) {
            assertNull(layer.image());
            assertFalse(layer.needsRender(), "nothing shown and nothing rendered: nothing to do");
            var picture = nv12(4, 2);
            layer.show(picture);
            assertSame(picture, layer.image());
            assertTrue(layer.needsRender());
        }
    }

    @Test
    @DisplayName("a part outside the picture, or of no area, is refused")
    void partIsChecked() {
        try (var layer = new VideoLayer()) {
            var picture = nv12(4, 2);
            assertThrows(IllegalArgumentException.class, () -> layer.show(picture, PhysicalRect.of(2, 0, 3, 2)));
            assertThrows(IllegalArgumentException.class, () -> layer.show(picture, PhysicalRect.of(0, 0, 0, 2)));
            layer.show(picture, PhysicalRect.of(1, 0, 3, 2));
        }
    }

    @Test
    @DisplayName("a closed layer needs nothing, and closes once")
    void closed() {
        var layer = new VideoLayer();
        layer.show(nv12(4, 2));
        layer.close();
        layer.close();
        assertFalse(layer.needsRender());
        assertNull(layer.image());
    }
}
