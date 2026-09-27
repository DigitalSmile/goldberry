package io.github.digitalsmile.goldberry.media.view;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Objects;
import java.util.function.Consumer;

import org.jspecify.annotations.Nullable;

import io.github.digitalsmile.goldberry.gpu.video.ColorMatrix;
import io.github.digitalsmile.goldberry.gpu.video.PlaneLayout;
import io.github.digitalsmile.goldberry.gpu.video.VideoImage;
import io.github.digitalsmile.goldberry.gpu.video.VideoLayer;
import io.github.digitalsmile.goldberry.media.Picture;
import io.github.digitalsmile.goldberry.media.VideoPicture;
import io.github.digitalsmile.goldberry.media.VideoPlanes;
import io.github.digitalsmile.goldberry.media.codec.PixelFormat;
import io.github.digitalsmile.goldberry.media.codec.VideoFrame;
import io.github.digitalsmile.goldberry.paint.Frame;
import io.github.digitalsmile.goldberry.widgets.core.image.Fit;

/// The [VideoPresenter] over `:gpu`'s [VideoLayer]: the one class of this
/// module that names `:gpu`'s types, loaded only when [GpuVideo#available()]
/// (ADR-0484).
///
/// Each picture becomes one [VideoImage], made when the picture is first
/// placed and handed to the layer again while the same picture is shown, since
/// the layer uploads an image it has not seen, by identity. Planes are shown as
/// planes, and converted pictures -- the ones queued before the player's form
/// changed, or all of them while another view on the player draws on the CPU --
/// as BGRA.
final class GpuVideoPresenter implements VideoPresenter {

    private final VideoLayer layer = new VideoLayer();
    private final Consumer<Boolean> shownOnGpu;
    private @Nullable Picture picture;
    private @Nullable VideoImage image;
    private @Nullable Boolean onGpu;

    GpuVideoPresenter(Consumer<Boolean> shownOnGpu) {
        this.shownOnGpu = Objects.requireNonNull(shownOnGpu, "shownOnGpu");
    }

    @Override
    public boolean place(Frame frame, Picture shown, Fit.Placement placement) {
        if (shown != picture) {
            picture = shown;
            image = image(shown);
        }
        layer.show(Objects.requireNonNull(image), placement.source());
        var placed = frame.gpuLayer(layer, placement.x(), placement.y(), placement.width(), placement.height());
        if (!Boolean.valueOf(placed).equals(onGpu)) {
            onGpu = placed;
            shownOnGpu.accept(placed);
        }
        return placed;
    }

    /// `picture` as the layer's vocabulary has it.
    static VideoImage image(Picture picture) {
        return switch (picture) {
            case VideoPicture bgra -> new VideoImage.Bgra(bgra.width(), bgra.height(), bgra.stride(), bgra.pixels());
            case VideoPlanes planes -> {
                var buffers = new ArrayList<ByteBuffer>(planes.format().planes());
                var strides = new ArrayList<Integer>(planes.format().planes());
                for (var plane = 0; plane < planes.format().planes(); plane++) {
                    buffers.add(planes.plane(plane));
                    strides.add(planes.stride(plane));
                }
                yield new VideoImage.Planes(
                        layout(planes.format()),
                        planes.width(),
                        planes.height(),
                        buffers,
                        strides,
                        matrix(planes.matrix()),
                        planes.fullRange());
            }
        };
    }

    private static PlaneLayout layout(PixelFormat format) {
        return switch (format) {
            case NV12 -> PlaneLayout.NV12;
            case I420 -> PlaneLayout.I420;
            case P010 -> PlaneLayout.P010;
            case I010 -> PlaneLayout.I010;
        };
    }

    private static ColorMatrix matrix(VideoFrame.ColorMatrix matrix) {
        return switch (matrix) {
            case BT601 -> ColorMatrix.BT601;
            case BT709 -> ColorMatrix.BT709;
            case BT2020 -> ColorMatrix.BT2020;
        };
    }

    /// The image the layer shows now, for the tests.
    @Nullable
    VideoImage layerImage() {
        return layer.image();
    }

    @Override
    public void close() {
        layer.close();
        picture = null;
        image = null;
    }

    @Override
    public String toString() {
        return "GpuVideoPresenter[" + layer + "]";
    }
}
