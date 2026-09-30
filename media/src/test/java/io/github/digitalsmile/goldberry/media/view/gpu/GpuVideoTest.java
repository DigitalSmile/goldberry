package io.github.digitalsmile.goldberry.media.view.gpu;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import io.github.digitalsmile.goldberry.RendererRequirement;
import io.github.digitalsmile.goldberry.gpu.video.ColorMatrix;
import io.github.digitalsmile.goldberry.gpu.video.PlaneLayout;
import io.github.digitalsmile.goldberry.gpu.video.VideoImage;
import io.github.digitalsmile.goldberry.media.codec.PixelFormat;
import io.github.digitalsmile.goldberry.media.codec.VideoFrame;
import io.github.digitalsmile.goldberry.media.picture.VideoPicture;
import io.github.digitalsmile.goldberry.media.picture.VideoPlanes;
import io.github.digitalsmile.goldberry.offscreen.Offscreen;
import io.github.digitalsmile.goldberry.render.model.PhysicalRect;
import io.github.digitalsmile.goldberry.widgets.core.image.Fit;

/// [GpuVideo] and [GpuVideoPresenter] with no device (ADR-0484): whether `:gpu`
/// is found, how a picture becomes the layer's image, and what a frame with no
/// GPU makes of it. The build runs this module's tests twice, with `:gpu` on
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
    @DisplayName("maps planes to the layer's vocabulary, layout, colour and all")
    void mapsPlanes() {
        assumeTrue(GpuVideo.available());
        for (var format : PixelFormat.values()) {
            var planes = new ArrayList<ByteBuffer>();
            var strides = new ArrayList<Integer>();
            for (var plane = 0; plane < format.planes(); plane++) {
                planes.add(ByteBuffer.allocateDirect(64));
                strides.add(format.planeRowBytes(plane, 4));
            }
            var picture = new VideoPlanes(format, 4, 2, planes, strides, VideoFrame.ColorMatrix.BT709, false, 0);
            var image = assertInstanceOf(VideoImage.Planes.class, GpuVideoPresenter.image(picture));
            assertEquals(PlaneLayout.valueOf(format.name()), image.layout());
            assertEquals(ColorMatrix.BT709, image.matrix());
            assertEquals(strides, image.strides());
            assertFalse(image.fullRange());
        }
        var full = (VideoImage.Planes) GpuVideoPresenter.image(nv12(4, 2));
        assertEquals(ColorMatrix.BT2020, full.matrix());
        assertEquals(true, full.fullRange());
    }

    @Test
    @DisplayName("maps a converted picture to BGRA over its own pixels")
    void mapsBgra() {
        assumeTrue(GpuVideo.available());
        var picture = new VideoPicture(4, 2, 16, ByteBuffer.allocateDirect(32), 0);
        var image = assertInstanceOf(VideoImage.Bgra.class, GpuVideoPresenter.image(picture));
        assertEquals(16, image.stride());
        assertEquals(4, image.width());
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
