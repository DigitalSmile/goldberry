package dev.goldberry.media.view.gpu;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import dev.goldberry.RendererRequirement;
import dev.goldberry.gpu.video.VideoImage;
import dev.goldberry.media.codec.PixelFormat;
import dev.goldberry.media.codec.VideoFrame;
import dev.goldberry.media.picture.VideoPlanes;
import dev.goldberry.offscreen.Offscreen;
import dev.goldberry.render.model.PhysicalRect;
import dev.goldberry.widgets.core.image.Fit;

/// [GpuVideo] and [GpuVideoPresenter] with no device: whether `:gpu`
/// is found, and what a frame with no GPU makes of a picture. How a picture
/// becomes the layer's image is [PicturesTest]'s. The build runs this module's tests twice, with `:gpu` on
/// the class path and without (`testWithoutGpu`), and says which in
/// `goldberry.test.gpuModule`.
@DisplayName("video-view's GPU presenter, with no device")
class GpuVideoTest {

    /// Whether the build put `:gpu` on this run's class path.
    private static boolean gpuModuleExpected() {
        return !"absent".equals(System.getProperty("goldberry.test.gpuModule"));
    }

    @Test
    @DisplayName("finds :gpu exactly when it is on the path, and makes a presenter only then")
    void availability() {
        assertEquals(gpuModuleExpected(), GpuVideo.available());
        var presenter = GpuVideo.presenter(_ -> {});
        assertEquals(gpuModuleExpected(), presenter != null);
        if (presenter != null) {
            presenter.close();
            presenter.close();
        }
    }

    private static VideoPlanes nv12(int width, int height) {
        return new VideoPlanes(
                PixelFormat.NV12,
                width,
                height,
                List.of(ByteBuffer.allocateDirect(width * height), ByteBuffer.allocateDirect(width * height / 2)),
                List.of(width, width),
                VideoFrame.ColorMatrix.BT2020,
                true,
                40);
    }

    @Test
    @DisplayName("in a frame with no GPU, places nothing, says so once, and leaves the CPU to draw")
    void noGpuInTheFrame() {
        assumeTrue(GpuVideo.available());
        RendererRequirement.enforce();
        var calls = new ArrayList<Boolean>();
        var presenter = new GpuVideoPresenter(calls::add);
        var picture = nv12(4, 2);
        var placement = Fit.FILL.place(4, 2, 4, 2, 8, 4);
        Offscreen.of(8, 4).paint((frame, size) -> {
            assertFalse(presenter.place(frame, picture, placement));
            assertFalse(presenter.place(frame, picture, placement));
        });
        assertEquals(List.of(false), calls, "reported when it changed, not on every paint");
        presenter.close();
    }

    @Test
    @DisplayName("makes one image for one picture, however often it is placed, and a new one for the next")
    void oneImagePerPicture() {
        assumeTrue(GpuVideo.available());
        RendererRequirement.enforce();
        var presenter = new GpuVideoPresenter(_ -> {});
        var first = nv12(4, 2);
        var second = nv12(4, 2);
        var placement = new Fit.Placement(PhysicalRect.of(1, 0, 3, 2), 0, 0, 8, 4);
        var seen = new ArrayList<VideoImage>();
        Offscreen.of(8, 4).paint((frame, size) -> {
            presenter.place(frame, first, placement);
            seen.add(presenter.layerImage());
            presenter.place(frame, first, placement);
            seen.add(presenter.layerImage());
            presenter.place(frame, second, placement);
            seen.add(presenter.layerImage());
        });
        assertSame(seen.get(0), seen.get(1));
        assertFalse(seen.get(1) == seen.get(2));
        presenter.close();
        assertNull(presenter.layerImage());
    }
}
