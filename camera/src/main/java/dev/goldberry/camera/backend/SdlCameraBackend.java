package dev.goldberry.camera.backend;

import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;

import dev.goldberry.camera.CameraFormat;
import dev.goldberry.camera.CameraInfo;
import dev.goldberry.camera.CameraPosition;
import dev.goldberry.natives.sdl.SdlException;
import dev.goldberry.natives.sdl.camera.SdlCamera;
import dev.goldberry.natives.sdl.camera.SdlCameraFrame;
import dev.goldberry.natives.sdl.camera.SdlCameraInfo;
import dev.goldberry.natives.sdl.camera.SdlCameraPixelFormat;
import dev.goldberry.natives.sdl.camera.SdlCameraSpec;
import dev.goldberry.natives.sdl.camera.SdlCameras;
import dev.goldberry.natives.sdl.device.SdlDeviceEvent;
import dev.goldberry.natives.sdl.device.SdlDeviceWatch;

/// The desktop's backend: SDL's cameras, an uncompressed mode opened in NV12
/// so SDL's camera thread converts what the camera sends, a compressed one in
/// Motion JPEG, which SDL hands through as the camera sent it; and its device
/// events, which reach a watch only while the window loop pumps.
public final class SdlCameraBackend implements CameraBackend {

    private final SdlCameras cameras;

    private SdlCameraBackend(SdlCameras cameras) {
        this.cameras = cameras;
    }

    /// Starts SDL's camera subsystem, or takes another reference on it.
    ///
    /// @throws CameraLostException when SDL has no camera driver to start
    public static SdlCameraBackend open() {
        try {
            return new SdlCameraBackend(SdlCameras.open());
        } catch (SdlException e) {
            throw new CameraLostException("SDL has no camera driver: " + e.getMessage(), e);
        }
    }

    @Override
    public List<CameraInfo> devices() {
        return cameras.cameras().stream().map(SdlCameraBackend::info).toList();
    }

    @Override
    public CameraStream open(int id, CameraFormat mode) {
        Objects.requireNonNull(mode, "mode");
        // The device's own colorspace, so SDL's conversion to NV12 keeps it.
        // A JPEG says its own; SDL reports it as the mode's.
        var colorspace = cameras.cameras().stream()
                .filter(camera -> camera.id() == id)
                .flatMap(camera -> camera.formats().stream())
                .filter(spec -> matches(spec, mode))
                .mapToInt(SdlCameraSpec::colorspace)
                .findFirst()
                .orElse(0);
        var format = mode.compressed() ? SdlCameraPixelFormat.MJPG : SdlCameraPixelFormat.NV12;
        var asked = new SdlCameraSpec(
                format.value(), colorspace, mode.width(), mode.height(), mode.rateNumerator(), mode.rateDenominator());
        try {
            return new Stream(cameras.open(id, asked));
        } catch (SdlException e) {
            throw new CameraLostException("SDL could not open the camera: " + e.getMessage(), e);
        }
    }

    @Override
    public AutoCloseable watch(Consumer<DeviceEvent> listener) {
        Objects.requireNonNull(listener, "listener");
        return SdlDeviceWatch.install(event -> {
            switch (event) {
                case SdlDeviceEvent.CameraAdded(var device) -> listener.accept(new DeviceEvent.Added(device));
                case SdlDeviceEvent.CameraRemoved(var device) -> listener.accept(new DeviceEvent.Removed(device));
                case SdlDeviceEvent.CameraApproved(var device) -> listener.accept(new DeviceEvent.Approved(device));
                case SdlDeviceEvent.CameraDenied(var device) -> listener.accept(new DeviceEvent.Denied(device));
                default -> {
                    // Audio devices are the microphone's.
                }
            }
        });
    }

    @Override
    public void close() {
        cameras.close();
    }

    /// SDL's description of a camera, in this module's words.
    static CameraInfo info(SdlCameraInfo camera) {
        var position = switch (camera.position()) {
            case FRONT_FACING -> CameraPosition.FRONT;
            case BACK_FACING -> CameraPosition.BACK;
            case UNKNOWN -> CameraPosition.UNKNOWN;
        };
        var formats = camera.formats().stream().map(SdlCameraBackend::format).toList();
        return new CameraInfo(camera.id(), camera.name(), position, formats);
    }

    /// One of SDL's modes. A format SDL names and this module does not (an RGB
    /// one) is not compressed: SDL converts it like the YUV ones.
    static CameraFormat format(SdlCameraSpec spec) {
        var compressed =
                spec.pixelFormat().map(SdlCameraPixelFormat::compressed).orElse(false);
        return new CameraFormat(
                spec.width(), spec.height(), spec.framerateNumerator(), spec.framerateDenominator(), compressed);
    }

    private static boolean matches(SdlCameraSpec spec, CameraFormat mode) {
        return spec.width() == mode.width()
                && spec.height() == mode.height()
                && spec.framerateNumerator() == mode.rateNumerator()
                && spec.framerateDenominator() == mode.rateDenominator()
                && spec.pixelFormat().map(SdlCameraPixelFormat::compressed).orElse(false) == mode.compressed();
    }

    /// SDL's camera, its frames read in place: NV12 planes, or Motion JPEG.
    private record Stream(SdlCamera camera) implements CameraStream {

        @Override
        public Permission permission() {
            return switch (camera.permission()) {
                case APPROVED -> Permission.APPROVED;
                case DENIED -> Permission.DENIED;
                case PENDING -> Permission.PENDING;
            };
        }

        @Override
        public boolean take(FrameSink sink) {
            var acquired = camera.acquire();
            if (acquired.isEmpty()) {
                return false;
            }
            try (var frame = acquired.get()) {
                sink.accept(lent(frame));
            }
            return true;
        }

        private LentFrame lent(SdlCameraFrame frame) {
            return switch (frame.pixelFormat().orElse(null)) {
                case NV12 -> {
                    var layout = frame.layout();
                    var colour = camera.format()
                            .map(spec -> Colorimetry.of(spec.colorspace()))
                            .orElse(Colorimetry.UNSAID);
                    yield new Nv12Frame(
                            frame.pixels(),
                            frame.width(),
                            frame.height(),
                            layout.offsets().get(0),
                            layout.strides().get(0),
                            layout.offsets().get(1),
                            layout.strides().get(1),
                            colour.matrix(),
                            colour.fullRange(),
                            frame.timestampNanos());
                }
                // SDL reports a compressed frame's length as its pitch, which
                // is the extent the frame's buffer has.
                case MJPG -> new JpegFrame(frame.pixels(), frame.width(), frame.height(), frame.timestampNanos());
                case null, default ->
                    throw new CameraLostException("the camera sent frames in SDL format "
                            + Integer.toHexString(frame.format()) + ", neither NV12 nor Motion JPEG");
            };
        }

        @Override
        public void close() {
            camera.close();
        }
    }
}
