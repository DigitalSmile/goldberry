package dev.goldberry.media.gpu;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.nio.ByteBuffer;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import dev.goldberry.media.codec.PixelFormat;
import dev.goldberry.media.codec.VideoFrame;
import dev.goldberry.media.picture.VideoPicture;
import dev.goldberry.media.picture.VideoPlanes;
import dev.goldberry.media.view.gpu.GpuVideo;
import dev.goldberry.render.model.PhysicalRect;

/// [PictureRenderer] with no device: one image per picture, told apart by
/// identity, and a closed renderer refusing before it looks at anything else.
/// Skipped in the run without `:gpu`; what it draws is
/// [PictureRendererOnGpuTest]'s.
@DisplayName("a picture renderer, with no device")
class PictureRendererTest {

    @BeforeEach
    void needsGpuModule() {
        assumeTrue(GpuVideo.available());
    }

    private static VideoPlanes nv12() {
        return new VideoPlanes(
                PixelFormat.NV12,
                4,
                2,
                List.of(ByteBuffer.allocateDirect(8), ByteBuffer.allocateDirect(4)),
                List.of(4, 4),
                VideoFrame.ColorMatrix.BT709,
                false,
                0);
    }

    @Test
    @DisplayName("maps a picture once however often it is rendered, and the next picture anew")
    void oneImagePerPicture() {
        try (var renderer = new PictureRenderer()) {
            var first = nv12();
            var second = nv12();
            var image = renderer.image(first);
            assertSame(image, renderer.image(first));
            assertNotSame(image, renderer.image(second));
            assertSame(renderer.image(second), renderer.image(second));

            var converted = new VideoPicture(4, 2, 16, ByteBuffer.allocateDirect(32), 0);
            assertSame(renderer.image(converted), renderer.image(converted));
        }
    }

    @Test
    @DisplayName("refuses once closed, whatever it is handed, and closes twice quietly")
    void closedRefuses() {
        var renderer = new PictureRenderer();
        renderer.close();
        assertDoesNotThrow(renderer::close);
        var picture = nv12();
        assertThrows(IllegalStateException.class, () -> renderer.render(null, picture, null));
        assertThrows(
                IllegalStateException.class, () -> renderer.render(null, picture, PhysicalRect.of(0, 0, 2, 2), null));
    }
}
