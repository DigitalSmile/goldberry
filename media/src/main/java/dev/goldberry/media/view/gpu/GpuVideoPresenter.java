package dev.goldberry.media.view.gpu;

import java.util.Objects;
import java.util.function.Consumer;

import org.jspecify.annotations.Nullable;

import dev.goldberry.gpu.video.VideoImage;
import dev.goldberry.gpu.video.VideoLayer;
import dev.goldberry.media.picture.Picture;
import dev.goldberry.paint.Frame;
import dev.goldberry.widgets.core.image.Fit;

/// The [VideoPresenter] over `:gpu`'s [VideoLayer], loaded only when
/// [GpuVideo#available()].
///
/// Each picture becomes one [VideoImage] through [Pictures], made when the
/// picture is first placed and handed to the layer again while the same
/// picture is shown, since the layer uploads an image it has not seen, by
/// identity. Planes are shown as planes, and converted pictures -- the ones
/// queued before the player's form changed, or all of them while another view
/// on the player draws on the CPU -- as BGRA.
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
            image = Pictures.image(shown);
        }
        layer.show(Objects.requireNonNull(image), placement.source());
        var placed = frame.gpuLayer(layer, placement.x(), placement.y(), placement.width(), placement.height());
        if (!Boolean.valueOf(placed).equals(onGpu)) {
            onGpu = placed;
            shownOnGpu.accept(placed);
        }
        return placed;
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
